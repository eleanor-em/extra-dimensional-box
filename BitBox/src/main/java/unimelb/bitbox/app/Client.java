package unimelb.bitbox.app;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import unimelb.bitbox.client.requests.ClientArgsException;
import unimelb.bitbox.client.requests.ClientRequestProtocol;
import unimelb.bitbox.util.crypto.Crypto;
import unimelb.bitbox.util.crypto.CryptoException;
import unimelb.bitbox.util.functional.algebraic.ChainedEither;
import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.util.Base64;

/**
 * Contains the main method for the Client.
 */
public class Client {
    /**
     * As per the specification.
     */
    public static final String PRIVATE_KEY_FILE = "bitboxclient_rsa";

    /**
     * Generates the command line options object as per the specification.
     *
     * @return the created Options
     */
    private static Options generateCLIOptions() {
        Options options = new Options();
        options.addOption("c", "command", true, "command to run");
        options.addOption("s", "server-address", true, "address of the server peer");
        options.addOption("p", "peer-address", true, "peer to connect to or disconnect from");
        options.addOption("i", "identity", true, "identity to use for connection");
        return options;
    }

    private static Result<ClientArgsException, String> getFromOpts(CommandLine opts, String key) {
        if (!opts.hasOption(key)) {
            return Result.error(new ClientArgsException("missing command line option: -" + key));
        }
        return Result.value(opts.getOptionValue(key));
    }

    public static void main(String[] args) {
        ServerConnection.initialise(args)
                        .match(err -> System.out.println(err.getMessage()),
                               connection -> {
            // Create streams
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(connection.socket.getOutputStream()));
                 BufferedReader in = new BufferedReader(new InputStreamReader(connection.socket.getInputStream()))) {
                // Send authentication request
                out.write(generateAuthRequest(connection.ident).networkEncode());
                out.flush();

                // Wait for authentication response
                String responseText = in.readLine();
                if (responseText == null) {
                    System.out.println("No response");
                    return;
                }
                // Parse the response
                Result.of(() -> new AuthResponseParser(responseText))
                        // Check response for errors, and decrypt it
                        .andThen(response -> {
                            if (response.isError()) {
                                return Result.error(new ClientError("Authentication failure: " + response.getMessage()));
                            } else {
                                return response.decryptKey(connection.privateKey);
                            }
                        })
                        .andThen(key -> Crypto.encryptMessage(key, connection.request).mapError(ClientError::new)
                                .andThen(encryptedRequest -> Result.of(() -> {
                                    // Send the encrypted message over the network
                                    out.write(encryptedRequest.networkEncode());
                                    out.flush();
                                    return key;
                                }).mapError(ClientError::new)))
                        .map(key -> Result.of(in::readLine).mapError(ClientError::new)
                                // Parse the response
                                .andThen(encryptedResponse -> JSONDocument.parse(encryptedResponse).mapError(ClientError::new))
                                .andThen(encryptedResponse -> {
                                    // Make sure we actually got a response
                                    if (encryptedResponse.isEmpty()) {
                                        return Result.value("No response");
                                    } else if (encryptedResponse.containsKey("status")) {
                                        // Check if the response contained an error
                                        return encryptedResponse.getBoolean("status")
                                                .map(status -> {
                                                    if (!status) {
                                                        return "Failed response: " + encryptedResponse.getString("message");
                                                    } else {
                                                        return "Malformed response: " + encryptedResponse;
                                                    }
                                                })
                                                .mapError(ClientError::new);
                                    } else {
                                        // Validation done, decrypt the message
                                        return Crypto.decryptMessage(key, encryptedResponse)
                                                .map(JSONDocument::toString)
                                                .mapError(ClientError::new);
                                    }
                                })
                                .consumeError(ClientError::getMessage))
                        .collapse(System.out::println);
            } catch (IOException e) {
                System.out.println("Error reading/writing socket: " + e.getMessage());
            } finally {
                // Make sure we close the socket!
                try {
                    connection.socket.close();
                } catch (IOException e) {
                    System.out.println("Error closing socket: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Generates an authentication request for the provided identity.
     *
     * @param ident the identity to request authentication for
     * @return the JSON message to send
     */
    private static JSONDocument generateAuthRequest(String ident) {
        JSONDocument authRequest = new JSONDocument();
        authRequest.append("command", "AUTH_REQUEST");
        authRequest.append("identity", ident);
        return authRequest;
    }

    private static class ServerConnection {
        public final Socket socket;
        public final String ident;
        public final JSONDocument request;
        public final PrivateKey privateKey;

        public static Result<ClientArgsException, ServerConnection> initialise(String[] args) {
            // Parse the command line options
            return Result.of(() -> new DefaultParser().parse(generateCLIOptions(), args))
                    .or(Result.error(new ClientArgsException("failed to parse command line options")))
                    .map(ArgsData::new)
                    // Get the identity
                    .peek(data -> getFromOpts(data.opts, "i").ok(ident -> data.ident = ident))
                    // Get the server name
                    .peek(data -> getFromOpts(data.opts, "s")
                            // Translate to HostPort
                            .andThen(addr -> HostPort.fromAddress(addr)
                            .mapError(ClientArgsException::new)
                            .ok(hp -> data.hostPort = hp)))
                    // Generate the request
                    .peek(data -> ClientRequestProtocol.generateMessage(data.opts).ok(req -> data.request = req))
                    // Create the socket
                    .ok(data -> Result.of(() -> new Socket(data.hostPort.hostname, data.hostPort.port))
                                      .mapError(e -> new ClientArgsException("failed to create socket: " + e.getMessage()))
                                      .ok(sock -> data.socket = sock))
                    // Create the resulting object
                    .andThen(data -> Result.of(() -> new ServerConnection(data))
                    .mapError(e -> new ClientArgsException("failed reading private key: " + e.getMessage())));
        }

        private ServerConnection(ArgsData data) throws IOException {
            this.socket = data.socket;
            this.ident = data.ident;
            this.request = data.request;

            Security.addProvider(new BouncyCastleProvider());
            PEMParser pemParser = new PEMParser(new FileReader(new File(Client.PRIVATE_KEY_FILE)));
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            KeyPair kp = converter.getKeyPair((PEMKeyPair) pemParser.readObject());
            privateKey = kp.getPrivate();
        }

        private static class ArgsData {
            public final CommandLine opts;
            public String ident;
            public HostPort hostPort;
            public JSONDocument request;
            public Socket socket;

            public ArgsData(CommandLine opts) {
                this.opts = opts;
            }
        }
    }

    private static class AuthResponseParser {
        private boolean status;
        private byte[] key;
        private String message;

        /**
         * Extract the data from the provided message.
         * @param message the JSON data to interpret
         * @throws ClientError in case the provided message is malformed
         */
        public AuthResponseParser(String message) throws ClientError {
            JSONDocument doc;
            try {
                doc = JSONDocument.parse(message).get();

                status = doc.getBoolean("status").get();
                this.message = doc.getString("message").get();
                if (status) {
                    String keyVal = doc.getString("AES128").get();
                    key = Base64.getDecoder().decode(keyVal);
                }
            } catch (JSONException e) {
                throw new ClientError(e);
            }
        }

        public Result<ClientError, SecretKey> decryptKey(PrivateKey privateKey) {
            return Crypto.decryptSecretKey(key, privateKey).mapError(ClientError::new);
        }

        /**
         * @return whether the response is in an error state
         */
        public boolean isError() {
            return !status;
        }

        /**
         * @return the message provided with the response, if there is one; otherwise, empty string
         */
        public String getMessage() {
            return message;
        }
    }

    private static class ClientError extends Exception {
        private ChainedEither<Exception, IOException, CryptoException, Exception> exception;

        public ClientError(String message) {
            super(message);
            exception = ChainedEither.left(new Exception(message));
        }

        public ClientError(IOException e) {
            exception = ChainedEither.middle(e);
        }

        public ClientError(CryptoException e) {
            exception = ChainedEither.right(e);
        }

        public ClientError(JSONException e) {
            this(new CryptoException(e));
        }

        @Override
        public String getMessage() {
            return exception.resolve().getMessage();
        }

        @Override
        public void printStackTrace() {
            exception.match(ignored -> super.printStackTrace(),
                    Exception::printStackTrace,
                    Exception::printStackTrace);
        }
    }
}