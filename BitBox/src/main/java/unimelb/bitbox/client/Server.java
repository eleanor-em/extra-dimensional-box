package unimelb.bitbox.client;

import org.json.simple.parser.ParseException;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.client.responses.ClientResponse;
import unimelb.bitbox.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Optional;

/**
 * An example class that acts as a server for the client.
 * To integrate with the project, this code should be adapted to fit into ServerMain.
 */
public class Server implements Runnable {
    // ELEANOR: Nothing needs to be static here; since we instantiate the class, better to be consistent.
    private final int clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
    private final String authorized_keys = Configuration.getConfigurationValue("authorized_keys");
    private final ArrayList<SSHPublicKey> keys = new ArrayList<>();
    private SecretKey key;
    private boolean authenticated;
    private ServerMain server;

    public Server(ServerMain server) {
        this.server = server;
    }

    @Override
    public void run() {
        // Load the public keys
        String[] keyStrings = authorized_keys.split(",");
        for (String keyString : keyStrings) {
            try {
                keys.add(new SSHPublicKey(keyString));
            } catch (InvalidKeyException e) {
                System.out.println("warning: invalid keystring " + keyString + ": " + e.getMessage());
            }
        }

        // Accept connections repeatedly.
        try (ServerSocket serverSocket = new ServerSocket(clientPort)) {
            while (true) {
                authenticated = false;
                Socket socket = serverSocket.accept();

                // Open the read/write streams and process messages until the socket closes
                try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    while (true) {
                        // Read a message, and pass it to the handler
                        String message = in.readLine();
                        if (message != null) {
                            try {
                                handleMessage(message, out);
                            } catch (ParseException | ResponseFormatException e) {
                                System.out.println("malformed message: " + e.getMessage());
                            }
                        } else {
                            // If we received null, the client disconnected
                            break;
                        }
                    }
                } catch (IOException | IllegalBlockSizeException | InvalidKeyException | BadPaddingException
                        | NoSuchAlgorithmException | NoSuchPaddingException | IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(String message, BufferedWriter out)
            throws IOException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException,
            NoSuchAlgorithmException, NoSuchPaddingException, ResponseFormatException, ParseException {
        System.out.println(message);
        // Parse the message. If there is a payload key, then we need to decrypt the payload to get the actual message
        JsonDocument document = JsonDocument.parse(message);
        if (document.containsKey("payload")) {
            document = JsonDocument.parse(Crypto.decryptMessage(key, message));
        }

        // Generate a response
        JsonDocument response = new JsonDocument();

        String command = document.require("command");

        if ("AUTH_REQUEST".equals(command)) {
            String ident = document.require("identity");
            response.append("command", "AUTH_RESPONSE");

            // Look up the provided ident in our list of keys to find the relevant key
            // (if there are several matching idents, just pick the first)
            Optional<SSHPublicKey> matchedKey = keys.stream()
                    .filter(key -> key.getIdent().equals(ident))
                    .findFirst();
            if (matchedKey.isPresent()) {
                try {
                    // We attempt to generate a key, and then encrypt it with the looked-up public key
                    key = Crypto.generateSecretKey();
                    response.append("AES128", Crypto.encryptSecretKey(key, matchedKey.get().getKey()));
                    response.append("status", true);
                    response.append("message", "public key found");
                } catch (NoSuchAlgorithmException | NoSuchPaddingException | BadPaddingException
                        | IllegalBlockSizeException | InvalidKeyException e) {
                    // In case the crypto algorithms failed, we send a failure response
                    System.out.println("Failed encryption: " + e.getMessage());
                    response.append("status", false);
                    response.append("message", "error generating key");
                }
            } else {
                // If the ident wasn't found, inform the user
                response.append("status", false);
                response.append("message", "public key not found");
            }
        } else {
            response = ClientResponse.getResponse(command, server, document);
        }

        String responseMessage = response.toJson();

        if (authenticated) {
            responseMessage = Crypto.encryptMessage(key, responseMessage);
        }
        out.write(responseMessage + "\n");
        out.flush();

        authenticated = true;
    }
}
