package unimelb.bitbox;

import org.apache.commons.cli.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import unimelb.bitbox.client.AuthResponse;
import unimelb.bitbox.client.ClientMessage;
import unimelb.bitbox.util.Crypto;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.ResponseFormatException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.*;

/**
 * Contains the main method for the Client.
 */
public class Client {
    /**
     * As per the specification.
     */
    private static final String PRIVATE_KEY_FILE = "bitboxclient_rsa";

    /**
     * Generates the command line options object as per the specification.
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

    /**
     * Loads our private key from the set file.
     * @return the created private key object
     * @throws IOException in case of IO error
     */
    private static PrivateKey getPrivateKey()
            throws IOException {
        Security.addProvider(new BouncyCastleProvider());
        PEMParser pemParser = new PEMParser(new FileReader(new File(Client.PRIVATE_KEY_FILE)));
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        KeyPair kp = converter.getKeyPair((PEMKeyPair)pemParser.readObject());
        return kp.getPrivate();
    }

    public static void main(String[] args) {
        // Parse the command line options
        CommandLineParser parser = new DefaultParser();
        CommandLine opts;
        try {
            opts = parser.parse(generateCLIOptions(), args);
        } catch (ParseException e) {
            System.out.println("Failed to parse command line options: " + e.getMessage());
            return;
        }

        // Extract the user identity from the options
        String ident = opts.getOptionValue("i");
        if (ident == null) {
            System.out.println("missing command line option: -i");
            return;
        }

        // Find the address we want to connect to, and create the message to be sent post-authentication
        HostPort hostPort;
        ClientMessage message;
        try {
            // Load the server address and perform error checking
            String serverAddress = opts.getOptionValue("s");
            if (serverAddress == null) {
                System.out.println("missing command line option: -s");
                return;
            }
            hostPort = new HostPort(serverAddress);

            message = ClientMessage.generateMessage(opts);
        } catch (IllegalArgumentException e) {
            System.out.println("Failed to parse command line options: " + e.getMessage());
            return;
        }

        // Load the private key
        PrivateKey privateKey;
        try {
            privateKey = getPrivateKey();
        } catch (IOException e) {
            System.out.println("Error reading private key: " + e.getMessage());
            return;
        }

        // Connect to the server
        Socket socket;
        try {
            socket = new Socket(hostPort.hostname, hostPort.port);
        } catch (UnknownHostException e) {
            System.out.println("invalid hostname: " + hostPort.hostname);
            return;
        } catch (IOException e) {
            System.out.println("failed creating socket: " + e.getMessage());
            return;
        }

        // Create streams
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            // Send authentication request
            out.write(generateAuthRequest(ident) + "\n");
            out.flush();

            // Wait for authentication response
            AuthResponse response = new AuthResponse(in.readLine());
            if (response.isError()) {
                System.out.println("Authentication failure: " + response.getMessage());
                return;
            }
            SecretKey key = response.decryptKey(privateKey);

            // Send encrypted message
            out.write(Crypto.encryptMessage(key, message.encoded()) + "\n");
            out.flush();

            // Wait for response
            String encryptedResponse = in.readLine();
            System.out.println(Crypto.decryptMessage(key, encryptedResponse));
        } catch (IOException e) {
            System.out.println("Error reading/writing socket: " + e.getMessage());
        } catch (ResponseFormatException e) {
            System.out.println("Peer sent invalid response: " + e.getMessage());
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException
                | BadPaddingException e) {
            System.out.println("Error decrypting secret key: " + e.getMessage());
        } finally {
            // Make sure we close the socket!
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    /**
     * Generates an authentication request for the provided identity.
     * @param ident the identity to request authentication for
     * @return the JSON message to send
     */
    private static String generateAuthRequest(String ident) {
        Document authRequest = new Document();
        authRequest.append("command", "AUTH_REQUEST");
        authRequest.append("identity", ident);
        return authRequest.toJson();
    }

}