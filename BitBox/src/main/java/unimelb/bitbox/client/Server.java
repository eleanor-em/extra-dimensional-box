package unimelb.bitbox.client;

import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.client.responses.ClientResponse;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.crypto.Crypto;
import unimelb.bitbox.util.crypto.CryptoException;
import unimelb.bitbox.util.crypto.SSHPublicKey;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.ResponseFormatException;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements Runnable {
    private static final CfgValue<Integer> clientPort = CfgValue.createInt("clientPort");
    private static final CfgValue<String> authorizedKeys = CfgValue.create("authorized_keys");

    private final Set<SSHPublicKey> keys = new HashSet<>();
    private SecretKey key;
    private ServerMain server;

    private ExecutorService pool = Executors.newCachedThreadPool();

    public Server(ServerMain server) {
        this.server = server;
        authorizedKeys.setOnChanged(this::loadKeys);
    }

    private void loadKeys() {
        synchronized (keys) {
            // Load the public keys
            String[] keyStrings = authorizedKeys.get().split(",");
            for (String keyString : keyStrings) {
                try {
                    keys.add(new SSHPublicKey(keyString.trim()));
                } catch (InvalidKeyException e) {
                    ServerMain.log.warning("invalid keystring " + keyString + ": " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(ClientData client) {
        Socket socket = client.getSocket();
        ServerMain.log.info(client + ": received connection");

        // Open the read/write streams and process messages until the socket closes
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while ((message = in.readLine()) != null) {
                // Read a message, and pass it to the handler
                try {
                    out.write(handleMessage(message, client).networkEncode());
                    out.flush();
                } catch (ResponseFormatException e) {
                    ServerMain.log.warning(client + ": malformed message: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            ServerMain.log.warning(client + ": error while responding: " + e.getMessage());
        }

        ServerMain.log.info(client + ": disconnected");
    }

    @Override
    public void run() {
        // Accept connections repeatedly.
        try (ServerSocket serverSocket = new ServerSocket(clientPort.get())) {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                pool.submit(() -> handleClient(new ClientData(socket)));
            }
        } catch (IOException e) {
            ServerMain.log.severe("Error with server socket:");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ServerMain.log.severe("Restarting server thread");
            new Thread(new Server(server)).start();
        }
    }

    private JSONDocument handleMessage(String message, ClientData client)
            throws ResponseFormatException {
        JSONDocument response = new JSONDocument();
        ServerMain.log.info(client + ": received " + message);
        // Parse the message. If there is a payload key, then we need to decrypt the payload to get the actual message
        JSONDocument document;
        String command;

        try {
            document = JSONDocument.parse(message);

            if (document.containsKey("payload")) {
                try {
                    document = Crypto.decryptMessage(key, document);
                } catch (CryptoException e) {
                    response.append("command", "AUTH_RESPONSE");
                    response.append("status", false);
                    response.append("message", "failed decrypting request: " + e.getMessage());
                    return response;
                }
            }

            command = document.require("command");
        } catch (ResponseFormatException e) {
            response.append("command", "AUTH_RESPONSE");
            response.append("status", false);
            response.append("message", "failed parsing request: " + e.getMessage());
            return response;
        }

        // Auth requests need to be handled separately because they rely on key data
        if (command.equals("AUTH_REQUEST")) {
            String ident = document.require("identity");
            client.setIdent(ident);
            response.append("command", "AUTH_RESPONSE");

            // Look up the provided ident in our list of keys to find the relevant key
            // (if there are several matching idents, just pick the first)
            Optional<SSHPublicKey> matchedKey;
            synchronized (keys) {
                matchedKey = keys.stream()
                        .filter(key -> key.getIdent().equals(ident))
                        .findFirst();
            }
            if (matchedKey.isPresent()) {
                try {
                    // We attempt to generate a key, and then encrypt it with the looked-up public key
                    key = Crypto.generateSecretKey();
                    String encryptedKey = Crypto.encryptSecretKey(key, matchedKey.get().getKey());
                    ServerMain.log.info(client + ": generated session key " + Base64.getEncoder().encodeToString(key.getEncoded()));
                    response.append("AES128", encryptedKey);
                    response.append("status", true);
                    response.append("message", "public key found");
                } catch (CryptoException e) {
                    // In case the crypto algorithms failed, we send a failure response
                    e.printStackTrace();
                    ServerMain.log.warning(ident + ": failed encryption: " + e.getMessage());
                    response.append("status", false);
                    response.append("message", "error generating key: " + e.toString());
                }
            } else {
                ServerMain.log.warning(ident + ": unknown ident");
                // If the ident wasn't found, inform the user
                response.append("status", false);
                response.append("message", "public key not found");
            }
        } else {
            response = ClientResponse.getResponse(command, server, document);
        }

        ServerMain.log.info(client + ": sending " + response);
        if (client.isAuthenticated()) {
            try {
                response = Crypto.encryptMessage(key, response);
            } catch (CryptoException e) {
                response.append("status", false);
                response.append("message", "failed encrypting response: " + e.getMessage());
            }
        }
        client.authenticate();
        return response;
    }
}

class ClientData {
    private Socket socket;
    private boolean authenticated;
    private String ident;

    private boolean anonymous = true;

    public ClientData(Socket socket) {
        this.socket = socket;
        ident = socket.getInetAddress().toString() + ":" + socket.getPort();
    }

    public Socket getSocket() {
        return socket;
    }

    public void setIdent(String ident) {
        if (anonymous) {
            this.ident = ident;
            anonymous = false;
        }
    }

    public void authenticate() {
        if (!anonymous) {
            authenticated = true;
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getIdent() {
        return ident;
    }

    @Override
    public String toString() {
        if (!authenticated) {
            return ident + " (unauthenticated)";
        }
        return ident;
    }

    @Override
    public boolean equals(Object rhs) {
        return rhs instanceof ClientData && rhs.toString().equals(toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}