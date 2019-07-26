package unimelb.bitbox.client;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.config.Configuration;
import unimelb.bitbox.util.network.JSONDocument;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Runnable object that serves Client connections.
 *
 * @author Eleanor McMurtry
 * @author Andrea Law
 */
public class ClientServer {
    // Data used by the class
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    static public void run() {
        // Accept connections repeatedly.
        try (ServerSocket serverSocket = new ServerSocket(Configuration.getClientPort())) {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                pool.submit(() -> handleClient(socket));
            }
        } catch (IOException e) {
            PeerServer.log().severe("Error with server socket:");
            e.printStackTrace();
        }
    }

    /**
     * A Runnbale operation that performs a full client interaction.
     */
    private static void handleClient(Socket socket) {
        String client = socket.getInetAddress().toString();
        PeerServer.log().fine(client + ": received connection");

        // Open the read/write streams and process messages until the socket closes
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message = in.readLine();
            // Read a message, and pass it to the handler
            JSONDocument response = handleMessage(client, message);
            // Check the status of the response and report the error if it failed
            if (!response.getBoolean("status").orElse(false)) {
                String error = response.getString("message").orElse("<no message>");
                PeerServer.log().warning(client + ": unsuccessful message sent: " + error);
            }
            // Write the message
            try {
                out.write(response.networkEncode());
                out.flush();
            } catch (IOException e) {
                PeerServer.log().warning(client + ": error while responding: " + e.getMessage());
            }
        } catch (IOException e) {
            PeerServer.log().warning(client + ": error with I/O streams: " + e.getMessage());
        }

        PeerServer.log().fine(client + ": disconnected");
    }

    /**
     * Given a message and the client it was received from, generate an appropriate response.
     */
    private static JSONDocument handleMessage(String client, String message) {
        PeerServer.log().fine(client + ": received " + message);
        if (ClientCommand.isValid(message)) {
            switch (ClientCommand.valueOf(message)) {
                case STOP:
                    PeerServer.log().info("Received stop command.");
                    System.exit(0);
                    return null;
                case PING:
                    return new JSONDocument().append("command", "PING_RESPONSE")
                            .append("status", true);
                case LIST:
                    var peers = PeerServer.connection().getActivePeers();
                    var peersStr = new ArrayList<String>();
                    for (var peer : peers) {
                        peersStr.add(peer.toString());
                    }
                    return new JSONDocument().append("command", "LIST_RESPONSE")
                            .append("peers", peersStr)
                            .append("status", true);
            }
        }

        return new JSONDocument().append("command", "ERROR_RESPONSE")
                .append("message", "Unrecognised command: " + message)
                .append("status", false);
    }
}
