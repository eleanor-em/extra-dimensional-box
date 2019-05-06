package unimelb.bitbox;
import org.jetbrains.annotations.NotNull;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.*;

/**
 * A PeerConnection is a combination of an OutgoingConnection (used to write to the socket) and an IncomingConnection
 * (used to read from the socket). The OutgoingConnection has a BlockingQueue; messages to be sent should be placed in
 * this queue. The IncomingConnection relays messages to the ServerThread's queue.
 */
public class PeerConnection {
    public final String name;

    private final boolean wasOutgoing;

    private Socket socket;
    private OutgoingConnection outConn;
    public final ServerMain server;
    private int port;
    private String host;

    // ELEANOR: Added INACTIVE so that a peer waiting to be closed doesn't keep sending more messages.
    public enum State {
        WAIT_FOR_REQUEST,
        WAIT_FOR_RESPONSE,
        ACTIVE,
        CLOSED,
        INACTIVE
    }
    private State state;

    // Returns the current state of the PeerConnection
    State getState() {
        synchronized (this) {
            return state;
        }
    }

    // Activate the peer connection after a handshake is complete.
    // Optionally, allow the port to be updated.
    void activate() {
        activate(host, port);
    }
    void activate(String host, int port) {
        synchronized (this) {
            if (state != State.CLOSED && state != State.INACTIVE) {
                this.host = host;
                this.port = port;
                state = State.ACTIVE;
            }
        }
    }

    private void deactivate() {
        synchronized (this) {
            state = State.INACTIVE;
        }
    }

    // Returns true if this peer was an outgoing connection (i.e. was in the peers list)
    boolean getOutgoing() {
        return wasOutgoing;
    }

    public String getLocalHost() {
        return name.split("-")[1]
                   .split(":")[0];
    }
    public int getLocalPort() {
        return Integer.parseInt(name.split("-")[1]
                                    .split(":")[1]);
    }

    // this may need to be set; if this socket came from an accepted connection,
    // we don't know what address they're hosting from until we receive a handshake request
    public String getHost() {
        return host;
    }
    public int getPort() {
        return port;
    }

    String getPlainName() {
        return name.split("-")[0];
    }

    PeerConnection(String name, Socket socket, ServerMain server, State state) {
        this.name = name;
        this.socket = socket;
        this.server = server;
        this.state = state;

        host = socket.getInetAddress().getHostAddress();
        port = socket.getPort();

        wasOutgoing = state == State.WAIT_FOR_RESPONSE;

        outConn = new OutgoingConnection(socket);
        outConn.start();

        IncomingConnection inConn = new IncomingConnection(socket, this);
        inConn.start();

        if (state == State.WAIT_FOR_RESPONSE) {
            ServerMain.log.info(name + ": Sending handshake request");
            sendMessageInternal(new HandshakeRequest(getLocalHost(), getLocalPort()));
        }
    }

    // Closes this peer.
    void close() {
        // must be synchronised so *either* the server or the peer can initiate a close sequence
        synchronized (this) {
            if (state == State.CLOSED) {
                return;
            }
            ServerMain.log.info("Connection to peer `" + name + "` closed.");

            try {
                socket.close();
            } catch (IOException e) {
                ServerMain.log.severe("Error closing socket: " + e.getMessage());
            }
            state = State.CLOSED;

            server.closeConnection(this);
        }
    }

    /**
     * Send a message to this peer. Validates the state first.
     */
    void sendMessage(Message message) {
        sendMessage(message, () -> {});
    }

    /**
     * Send a message to this peer, and then close the connection.
     */
    void sendMessageAndClose(Message message) {
        sendMessage(message, this::close);
        deactivate();
    }

    /**
     * Implementation of the actual sendMessage code, to allow default parameters.
     */
    public void sendMessage(Message message, Runnable onSent) {
        State state = getState();
        if (state == State.ACTIVE) {
            sendMessageInternal(message, onSent);
        } else if (state != State.CLOSED && state != State.INACTIVE){
            final String err = "handshake must be completed first";
            ServerMain.log.info("Closing connection to " + name + ": " + err);
            sendMessageInternal(new InvalidProtocol(err), this::close);
        }
    }

    /**
     * Send a message to this peer, regardless of state.
     */
    private void sendMessageInternal(Message message) {
        sendMessageInternal(message, () -> {});
    }

    /**
     * Send a message to this peer, regardless of state. Allows a function to run after the message is sent.
     */
    private void sendMessageInternal(Message message, Runnable onSent) {
        message.setFriendlyName(name);
        ServerMain.log.info(name + " sent: " + message.getType());
        outConn.messages.add(new OutgoingMessage(message.encode(), onSent));
    }

    /**
     * This method is called when a message is received from this peer.
     */
    void receiveMessage(String message) {
        server.enqueueMessage(new ReceivedMessage(message, this));
    }

    public String toString() {
        return name + " (" + host + ":" + port + ")";
    }
    public boolean equals(Object other) {
        if (other instanceof PeerConnection) {
            PeerConnection rhs = (PeerConnection)other;
            return rhs.toString().equals(toString());
        }
        return false;
    }
}

/**
 * A class to pair a message with a function to run when the message is sent.
 */
class OutgoingMessage {
    public final String message;
    public final Runnable onSent;

    public OutgoingMessage(String message, Runnable onSent) {
        this.message = message;
        this.onSent = onSent;
    }
}

class OutgoingConnection extends Thread {
    private Socket socket;
    // the queue of messages waiting to be sent
    final BlockingQueue<OutgoingMessage> messages = new LinkedBlockingQueue<>();

    OutgoingConnection(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            while (true) {
                OutgoingMessage message = messages.take();
                out.write(message.message + "\n");
                out.flush();
                message.onSent.run();
            }
        } catch (IOException | InterruptedException e) {
            ServerMain.log.severe("Error writing to socket: " + e.getMessage());
        }
    }
}

class IncomingConnection extends Thread {
    private Socket socket;
    // the PeerConnection object that will forward our received messages
    private PeerConnection consumer;

    IncomingConnection(Socket socket, PeerConnection consumer) {
        this.socket = socket;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            while (true) {
                String message = in.readLine();
                if (message != null) {
                    consumer.receiveMessage(message);
                }
            }
        } catch (IOException e) {
            if (consumer.getState() != PeerConnection.State.CLOSED) {
                ServerMain.log.severe("Error reading from socket: " + e.getMessage());
                consumer.close();
            }
        }
    }
}