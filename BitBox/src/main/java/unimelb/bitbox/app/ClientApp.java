package unimelb.bitbox.app;

import functional.algebraic.Maybe;
import functional.algebraic.Result;
import functional.combinator.Combinators;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import unimelb.bitbox.client.requests.ClientArgsException;
import unimelb.bitbox.client.requests.ClientRequestProtocol;
import unimelb.bitbox.util.crypto.Crypto;
import unimelb.bitbox.util.crypto.CryptoException;
import unimelb.bitbox.util.functional.ChainedEither;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.security.PrivateKey;
import java.util.Base64;

/**
 * Class to initialise the Bitbox Client.
 * @author Eleanor McMurtry
 */
public class ClientApp {
    /**
     * Where the client should look for the private key
     */
    private static final String PRIVATE_KEY_FILE = "bitboxclient_rsa";

    private ClientApp() {
    }

    /**
     * Produce the command-line arguments for the program, and return it as an Options object.
     * @return the generated options
     */
    @SuppressWarnings({"StaticMethodOnlyUsedInOneClass", "WeakerAccess"})
    static Options generateCLIOptions() {
        Options options = new Options();
        options.addOption("c", "command", true, "command to run");
        options.addOption("s", "server-address", true, "address of the server peer");
        options.addOption("p", "peer-address", true, "peer to connect to or disconnect from");
        options.addOption("i", "identity", true, "identity to use for connection");
        return options;
    }

    public static void main(String[] args) {
        ServerConnection.initialise(args)
                        .match(connection -> {
            // Create streams
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(connection.socket.getOutputStream()));
                 BufferedReader in = new BufferedReader(new InputStreamReader(connection.socket.getInputStream()))) {
                // Send authentication request
                out.write(generateAuthRequest(connection.ident).networkEncode());
                out.flush();

                // Wait for authentication response
                Maybe<String> maybeResponse = Maybe.of(in.readLine());
                if (!maybeResponse.isJust()) {
                    System.out.println("No response");
                    return;
                }
                // Parse the response
                Result.of(() -> new AuthResponseParser(maybeResponse.get()))
                        // Check response for errors, and decrypt it
                        .andThen(response -> response.isError()
                                ? Result.error(new ClientError("Authentication failure: " + response.getMessage()))
                                : response.decryptKey(connection.privateKey))
                        .andThen(key -> Crypto.encryptMessage(key, connection.request).mapError(ClientError::new)
                                .andThen(encryptedRequest -> Result.of(() -> {
                                    // Write the encrypted message to the socket
                                    out.write(encryptedRequest.networkEncode());
                                    out.flush();
                                    return key;
                                }).mapError(ClientError::new)))
                        // Read a response from the socket
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
                                                .map(status -> status
                                                        ? "Malformed response: " + encryptedResponse
                                                        : "Failed response: " + encryptedResponse.getString("message"))
                                                .mapError(ClientError::new);
                                    } else {
                                        // Validation done, decrypt the message
                                        return Crypto.decryptMessage(key, encryptedResponse)
                                                .map(JSONDocument::toString)
                                                .mapError(ClientError::new);
                                    }
                                })
                                .matchThen(Combinators::id, ClientError::getMessage))
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
        }, err -> System.out.println(err.getMessage()));
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

    /**
     * Class to initialise a connection to the server, and contain related data.
     */
    private static class ServerConnection {
        public final Socket socket;
        final String ident;
        public final JSONDocument request;
        final PrivateKey privateKey;

        /**
         * Initialse the server connection with the given arguments.
         * @param args command-line arguments
         * @return a Result containing either the connection object, or an error
         */
        @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
        static Result<ServerConnection, ClientArgsException> initialise(String[] args) {
            // Parse the command line options
            return Result.of(() -> new DefaultParser().parse(generateCLIOptions(), args))
                    .or(Result.error(new ClientArgsException("failed to parse command line options")))
                    .map(ArgsData::new)
                    // Get the identity
                    .peek(data -> data.getFromOpts("i").ifOk(ident -> data.ident = ident))
                    // Get the server name
                    .peek(data -> data.getFromOpts("s")
                            // Translate to HostPort
                            .andThen(addr -> HostPort.fromAddress(addr)
                            .mapError(ClientArgsException::new)
                            .ifOk(hp -> data.hostPort = hp)))
                    // Generate the request
                    .peek(data -> ClientRequestProtocol.generateMessage(data.opts).ifOk(req -> data.request = req))
                    // Create the socket
                    .ifOk(data -> Result.of(() -> new Socket(data.hostPort.hostname, data.hostPort.port))
                                      .mapError(e -> new ClientArgsException("failed to create socket: " + e.getMessage()))
                                      .ifOk(sock -> data.socket = sock))
                    // Create the resulting object
                    .andThen(data -> Result.of(() -> new ServerConnection(data))
                    .mapError(e -> new ClientArgsException("failed reading private key: " + e.getMessage())));
        }

        /**
         * Load the private key, and store the data.
         */
        private ServerConnection(ArgsData data) throws IOException {
            socket = data.socket;
            ident = data.ident;
            request = data.request;
            privateKey = Crypto.getRSAPrivateKey(PRIVATE_KEY_FILE).get();
        }

        /**
         * Encapsulates the CommandLine object, and allows extra data to be produced.
         */
        private static class ArgsData {
            final CommandLine opts;
            String ident;
            public HostPort hostPort;
            public JSONDocument request;
            public Socket socket;

            ArgsData(CommandLine opts) {
                this.opts = opts;
            }

            /**
             * Looks up the key in the command line arguments, and returns the value, or an error if the option was not provided.
             */
            Result<String, ClientArgsException> getFromOpts(String key) {
                if (!opts.hasOption(key)) {
                    return Result.error(new ClientArgsException("missing command line option: -" + key));
                }
                return Result.value(opts.getOptionValue(key));
            }
        }
    }
}

/**
 * Object to parse an AUTH_RESPONSE message, and decrypt the key.
 */
class AuthResponseParser {
    private final boolean status;
    private Maybe<byte[]> key = Maybe.nothing();
    private final String message;

    /**
     * Extract the data from the provided message.
     * @param message the JSON data to interpret
     * @throws ClientError in case the provided message is malformed
     */
    AuthResponseParser(String message) throws ClientError {
        JSONDocument doc;
        try {
            doc = JSONDocument.parse(message).get();

            status = doc.getBoolean("status").get();
            this.message = doc.getString("message").get();
            if (status) {
                String keyVal = doc.getString("AES128").get();
                key = Maybe.just(Base64.getDecoder().decode(keyVal));
            }
        } catch (JSONException e) {
            throw new ClientError(e);
        }
    }

    /**
     * Attempt to decrypt the parsed key using the given private key.
     * @return a Result containing either the decrypted secret key, or an error.
     */
    Result<SecretKey, ClientError> decryptKey(PrivateKey privateKey) {
        if (!key.isJust()) {
            return Result.error(new ClientError("key not loaded"));
        }
        return Crypto.decryptSecretKey(key.get(), privateKey).mapError(ClientError::new);
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

/**
 * Wraps an error that can occur while executing the client.
 */
class ClientError extends Exception {
    private final ChainedEither<Exception, IOException, CryptoException, Exception> exception;

    /**
     * In the case that a general message needs to be used, we will use the superclass's stack trace.
     */
    ClientError(String message) {
        super(message);
        exception = ChainedEither.left(new Exception(message));
    }

    ClientError(IOException e) {
        exception = ChainedEither.middle(e);
    }

    ClientError(CryptoException e) {
        exception = ChainedEither.right(e);
    }

    ClientError(JSONException e) {
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