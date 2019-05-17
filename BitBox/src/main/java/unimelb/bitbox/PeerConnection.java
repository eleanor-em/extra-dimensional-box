package unimelb.bitbox;

import unimelb.bitbox.messages.HandshakeRequest;
import unimelb.bitbox.messages.InvalidProtocol;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.messages.ReceivedMessage;
import unimelb.bitbox.util.Configuration;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A PeerConnection is a combination of an OutgoingConnection (used to write to the socket) and an IncomingConnection
 * (used to read from the socket). The OutgoingConnection has a BlockingQueue; messages to be sent should be placed in
 * this queue. The IncomingConnection relays messages to the ServerThread's queue.
 */
public abstract class PeerConnection {
    public final String name;
    private final boolean wasOutgoing;

    protected OutgoingConnection outConn;
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

    protected State state;

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

    void activate(String host, long port) {
        synchronized (this) {
            if (state != State.CLOSED && state != State.INACTIVE) {
                this.host = host;
                this.port = (int) port;
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

    PeerConnection(String name, ServerMain server, State state, String host, int port) {
        this.name = name;
        this.server = server;
        this.state = state;
        this.host = host;
        this.port = port;
        wasOutgoing = state == State.WAIT_FOR_RESPONSE;
    }

    // Closes this peer.
    abstract void close();

    /**
     * Send a message to this peer. Validates the state first.
     */
    public void sendMessage(Message message) {
        sendMessage(message, () -> {});
    }

    /**
     * Send a message to this peer, and then close the connection.
     */
    public void sendMessageAndClose(Message message) {
        sendMessage(message, this::close);
        deactivate();
    }

    /**
     * Implementation of the actual sendMessage code, to allow default parameters.
     */
    private void sendMessage(Message message, Runnable onSent) {
        State state = getState();
        if (state == State.ACTIVE) {
            sendMessageInternal(message, onSent);
        } else if (state != State.CLOSED && state != State.INACTIVE) {
            final String err = "handshake must be completed first";
            ServerMain.log.info("Closing connection to " + name + ": " + err);
            sendMessageInternal(new InvalidProtocol(err), this::close);
        }
    }

    /**
     * Send a message to this peer, regardless of state.
     */
    protected void sendMessageInternal(Message message) {
        sendMessageInternal(message, () -> {
        });
    }

    /**
     * Send a message to this peer, regardless of state. Allows a function to run after the message is sent.
     */
    protected void sendMessageInternal(Message message, Runnable onSent) {
        message.setFriendlyName(name);
        ServerMain.log.info(name + " sent: " + message.getCommand());
        outConn.messages.add(new OutgoingMessage(message.encode(), onSent));
    }

    /**
     * This method is called when a message is received from this peer.
     */
    public void receiveMessage(String message) {
        server.enqueueMessage(new ReceivedMessage(message, this));
    }

    /**
     * This method is called when a message has been received from this peer and successfuly parsed.
     */
    public void notify(Message message) {}

    public String toString() {
        return name + " (" + host + ":" + port + ")";
    }

    public boolean equals(Object other) {
        if (other instanceof PeerConnection) {
            PeerConnection rhs = (PeerConnection) other;
            return rhs.toString().equals(toString());
        }
        return false;
    }
}

class PeerTCP extends PeerConnection {
    private Socket socket;

    PeerTCP(String name, Socket socket, ServerMain server, State state) {
        super(name, server, state, socket.getInetAddress().getHostAddress(), socket.getPort());
        this.socket = socket;
        outConn = new OutgoingConnection(socket);
        outConn.start();
        IncomingConnection inConn = new IncomingConnection(socket, this);
        inConn.start();

        if (state == State.WAIT_FOR_RESPONSE) {
            ServerMain.log.info(name + ": Sending handshake request");
            sendMessageInternal(new HandshakeRequest(getLocalHost(), getLocalPort()));
        }
    }

    @Override
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


}

class PeerUDP extends PeerConnection {
    private HashMap<String, RetryThread> retryThreads = new HashMap<>();

    private class RetryThread extends Thread {
        private Message message;
        private PeerUDP parent;
        private final int RETRY_COUNT;
        private final int RETRY_TIME;

        public RetryThread(PeerUDP parent, Message message) {
            this.parent = parent;
            this.message = message;

            // Load settings, or use default value
            int retryTime;
            int retryCount;
            try {
                retryCount = Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));
            } catch (NumberFormatException ignored) {
                retryCount = 5;
            }

            try {
                retryTime = Integer.parseInt(Configuration.getConfigurationValue("udpTimeout"));
            } catch (NumberFormatException ignored) {
                retryTime = 1000;
            }

            RETRY_COUNT = retryCount;
            RETRY_TIME = retryTime;

        }

        @Override
        public void run() {
            for (int retries = 0; retries < RETRY_COUNT; ++retries) {
                try {
                    Thread.sleep(RETRY_TIME);
                } catch (InterruptedException e) {
                    // Pretty sure this only happens if we get a successful response.
                    ServerMain.log.info("Stopping retry thread");
                    return;
                }
                ServerMain.log.info(parent.name + ": resending "  + message.getCommand() + ": expecting " + message.getSummary());
                parent.retryMessage(message);
            }
            parent.close();
        }
    }

    @Override
    void activate(String host, long port) {
        synchronized (this) {
            if (state != State.CLOSED && state != State.INACTIVE) {
                // UDP peer already has accurate host and port
                state = State.ACTIVE;
            }
        }
    }

    PeerUDP(String name, ServerMain server, State state, DatagramSocket socket, DatagramPacket packet) {
        super(name, server, state, packet.getAddress().toString().split("/")[1], packet.getPort());
        outConn = new OutgoingConnection(socket, packet);
        outConn.start();

        if (state == State.WAIT_FOR_RESPONSE) {
            ServerMain.log.info(name + ": Sending UDP handshake request");
            sendMessageInternal(new HandshakeRequest(getLocalHost(), getLocalPort()));
        }
    }

    @Override
    // Override to not close the socket, as it's shared between all peers.
    void close() {
        synchronized (this) {
            if (state == State.CLOSED) {
                return;
            }
            ServerMain.log.info("Connection to peer `" + name + "` closed.");
            state = State.CLOSED;
            server.closeConnection(this);
        }
    }

    public void retryMessage(Message message) {
        super.sendMessageInternal(message);
    }

    @Override
    protected void sendMessageInternal(Message message, Runnable onSent) {
        if (message.isRequest() && !retryThreads.containsKey(message.getSummary())) {
            ServerMain.log.info(name + ": waiting for response: " + message.getSummary());
            RetryThread thread = new RetryThread(this, message);
            retryThreads.put(message.getSummary(), thread);
            thread.start();
        }
        super.sendMessageInternal(message, onSent);
    }

    @Override
    public void notify(Message message) {
        if (!message.isRequest()) {
            ServerMain.log.info(name + ": notified response: " + message.getSummary());
            Optional.ofNullable(retryThreads.get(message.getSummary()))
                    .ifPresent(Thread::interrupt);
        }
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
    private DatagramSocket udpSocket;
    private String mode;
    private DatagramPacket packet;
    // the queue of messages waiting to be sent
    final BlockingQueue<OutgoingMessage> messages = new LinkedBlockingQueue<>();

    OutgoingConnection(Socket socket) {
        this.socket = socket;
        this.mode = "tcp";
    }

    OutgoingConnection(DatagramSocket socket, DatagramPacket packet) {
        this.udpSocket = socket;
        this.mode = "udp";
        this.packet = packet;
    }

    @Override
    public void run() {
        if (this.mode.equals("tcp")) {
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
        } else {
            while (true) {
                try {
                    OutgoingMessage message = messages.take();

                    // Datagram packets need to be null terminated.
                    byte[] buffer = Arrays.copyOf(message.message.getBytes(), message.message.length() + 1);
                    buffer[buffer.length - 1] = '\0';

                    packet.setData(buffer);
                    udpSocket.send(packet);
                    message.onSent.run();
                } catch (IOException | InterruptedException | NullPointerException e) {
                    ServerMain.log.severe("Error sending packet to UDP socket: " + e.getMessage());
                }
            }
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