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

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Runnable object that serves Client connections.
 */
public class ClientServer implements Runnable {
    // Config values
    private static final CfgValue<Integer> clientPort = CfgValue.createInt("clientPort");
    private static final CfgValue<String> authorizedKeys = CfgValue.create("authorized_keys");

    // Data used by the class
    private final Set<SSHPublicKey> keys = new HashSet<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    /**
     * Constructs a server instance, binding it to the given controller object.
     */
    public ClientServer() {
        authorizedKeys.setOnChanged(this::loadKeys);
        loadKeys();
    }

    @Override
    public void run() {
        // Accept connections repeatedly.
        try (ServerSocket serverSocket = new ServerSocket(clientPort.get())) {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                pool.submit(() -> handleClient(new ClientConnection(socket)));
            }
        } catch (IOException e) {
            PeerServer.logSevere("Error with server socket:");
            e.printStackTrace();
        }
    }

    /**
     * A Runnbale operation that performs a full client interaction.
     */
    private void handleClient(ClientConnection client) {
        Socket socket = client.getSocket();
        PeerServer.logInfo(client + ": received connection");

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
                    PeerServer.logWarning(client + ": unsuccessful message sent: " + error);
                }
                // Write the message
                try {
                    out.write(response.networkEncode());
                    out.flush();
                } catch (IOException e) {
                    PeerServer.logWarning(client + ": error while responding: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            PeerServer.logWarning(client + ": error with I/O streams: " + e.getMessage());
        }

        if (!client.isAuthenticated()) {
            KnownClientTracker.addClient(client);
        }
        PeerServer.logInfo(client + ": disconnected");
    }

    /**
     * Given a message and the client it was received from, generate an appropriate response.
     */
    private JSONDocument handleMessage(String message, ClientConnection client) {
        PeerServer.logInfo(client + ": received " + message);
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
                           return ClientResponse.getResponse(command, doc);
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

    /**
     * Given an exception, generate a failure response.
     */
    private static JSONDocument generateFailResponse(Exception error) {
        return generateFailResponse(error.getMessage());
    }

    /**
     * Generates a failure response with the given message.
     */
    static JSONDocument generateFailResponse(String message) {
        JSONDocument response = new JSONDocument();
        response.append("command", "AUTH_RESPONSE");
        response.append("status", false);
        response.append("message", message);
        return response;
    }

    /**
     * Generate an authentication response from the given request and client, or an error if the process fails.
     */
    private Result<ServerException, JSONDocument> generateAuthResponse(JSONDocument request, ClientConnection client) {
        return request.getString("identity")
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
                                        KnownClientTracker.addClient(client);
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

    /**
     * Load the list of public keys from the config file.
     */
    private void loadKeys() {
        synchronized (keys) {
            // Load the public keys
            String[] keyStrings = authorizedKeys.get().split(",");
            for (String keyString : keyStrings) {
                try {
                    keys.add(new SSHPublicKey(keyString.trim()));
                } catch (InvalidKeyException e) {
                    PeerServer.logWarning("invalid keystring " + keyString + ": " + e.getMessage());
                }
            }
        }
    }
}
