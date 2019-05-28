package unimelb.bitbox.client;

import unimelb.bitbox.client.responses.ClientResponse;
import unimelb.bitbox.client.responses.ServerException;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.crypto.Crypto;
import unimelb.bitbox.util.crypto.SSHPublicKey;
import unimelb.bitbox.util.functional.algebraic.Maybe;
import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class ClientServer implements Runnable {
    private static final CfgValue<Integer> clientPort = CfgValue.createInt("clientPort");
    private static final CfgValue<String> authorizedKeys = CfgValue.create("authorized_keys");

    private final Set<SSHPublicKey> keys = new HashSet<>();
    private final PeerServer server;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public ClientServer(PeerServer server) {
        this.server = server;
        authorizedKeys.setOnChanged(this::loadKeys);
        loadKeys();
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
            PeerServer.log.severe("Error with server socket:");
            e.printStackTrace();
        }
    }

    private void handleClient(ClientData client) {
        Socket socket = client.getSocket();
        PeerServer.log.info(client + ": received connection");

        // Open the read/write streams and process messages until the socket closes
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while ((message = in.readLine()) != null) {
                // Read a message, and pass it to the handler
                JSONDocument response = handleMessage(message, client);
                // Check the status of the response and report the error if it failed
                if (!response.getBoolean("status").orElse(false)) {
                    String error = response.getString("message").orElse("<no message>");
                    PeerServer.log.warning(client + ": unsuccessful message sent: " + error);
                }
                // Write the message
                try {
                    out.write(response.networkEncode());
                    out.flush();
                } catch (IOException e) {
                    PeerServer.log.warning(client + ": error while responding: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            PeerServer.log.warning(client + ": error with I/O streams: " + e.getMessage());
        }

        PeerServer.log.info(client + ": disconnected");
    }

    private JSONDocument handleMessage(String message, ClientData client) {
        PeerServer.log.info(client + ": received " + message);
        // Parse the message
        JSONDocument document;
        try {
            document = JSONDocument.parse(message).get();
        } catch (JSONException e) {
            return generateFailResponse(e.getMessage());
        }

        Result<ServerException, JSONDocument> decrypted = document.containsKey("payload")
                ? client.bindKey(key -> Crypto.decryptMessage(key, document)).mapError(ServerException::new)
                : Result.value(document);

        Result<ServerException, JSONDocument> response = decrypted.andThen(doc ->
                doc.getString("command")
                   .mapError(ServerException::new)
                   .andThen(command -> {
                       if (command.equals("AUTH_REQUEST")) {
                           return generateAuthResponse(document, client);
                       } else {
                           return ClientResponse.getResponse(command, server, doc);
                       }
                   }));

        if (client.sentKey()) {
            response = response.map(responseDocument ->
                                    client.mapKey(key -> Crypto.encryptMessage(key, responseDocument)
                                    // Check for encryption errors
                                    .consumeError(err -> generateFailResponse("failed encrypting response: " + err.getMessage()))));
        }

        return response.consumeError(ClientServer::generateFailResponse);
    }

    private static JSONDocument generateFailResponse(Exception error) {
        return generateFailResponse(error.getMessage());
    }

    private static JSONDocument generateFailResponse(String message) {
        JSONDocument response = new JSONDocument();
        response.append("command", "AUTH_RESPONSE");
        response.append("status", false);
        response.append("message", message);
        return response;
    }

    private Result<ServerException, JSONDocument> generateAuthResponse(JSONDocument document, ClientData client) {
        return document.getString("identity")
                .map(ident -> {
                    client.setIdent(ident);

                    JSONDocument response = new JSONDocument();
                    response.append("command", "AUTH_RESPONSE");

                    // Look up the provided ident in our list of keys to find the relevant key
                    // (if there are several matching idents, just pick the first)
                    Maybe<SSHPublicKey> matchedKey;
                    synchronized (keys) {
                        matchedKey = Maybe.of(keys.stream()
                                .filter(key -> key.getIdent().equals(ident))
                                .findFirst());
                    }

                    return matchedKey.map(publicKey -> Crypto.generateSecretKey()
                                    .andThen(key -> {
                                        client.authenticate(key);
                                        return Crypto.encryptSecretKey(key, publicKey.getKey());
                                    })
                                    .matchThen(err -> {
                                                err.printStackTrace();
                                                response.append("status", false);
                                                response.append("message", "error encrypting key: " + err.toString());
                                                return response;
                                            },
                                            encryptedKey -> {
                                                response.append("AES128", encryptedKey);
                                                response.append("status", true);
                                                response.append("message", "public key found");
                                                return response;
                                            }))
                            .fromMaybe(() -> {
                                response.append("status", false);
                                response.append("message", "public key not found");
                                return response;
                            });
                }).mapError(ServerException::new);
    }

    private void loadKeys() {
        synchronized (keys) {
            // Load the public keys
            String[] keyStrings = authorizedKeys.get().split(",");
            for (String keyString : keyStrings) {
                try {
                    keys.add(new SSHPublicKey(keyString.trim()));
                } catch (InvalidKeyException e) {
                    PeerServer.log.warning("invalid keystring " + keyString + ": " + e.getMessage());
                }
            }
        }
    }

    private class ClientData {
        private Socket socket;
        private boolean authenticated;
        private boolean sentKey = false;
        private String ident;
        private SecretKey key;

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

        public void authenticate(SecretKey key) {
            if (!anonymous) {
                authenticated = true;
                assert key != null;
                this.key = key;
            }
        }

        public <E extends Exception> Result<E, JSONDocument> bindKey(Function<SecretKey, Result<E, JSONDocument>> op) {
            if (authenticated) {
                return op.apply(key);
            }
            return Result.value(ClientServer.generateFailResponse("client not authenticated"));
        }
        public JSONDocument mapKey(Function<SecretKey, JSONDocument> op) {
            if (authenticated) {
                return op.apply(key);
            }
            return ClientServer.generateFailResponse("client not authenticated");
        }

        public boolean sentKey() {
            boolean ret = sentKey;
            sentKey = true;
            return ret;
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
}