package unimelb.bitbox.peers;

import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.messages.HandshakeRequest;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.messages.ReceivedMessage;
import unimelb.bitbox.util.network.HostPort;

/**
 * A PeerConnection is a combination of an OutgoingConnection (used to write to the socket) and an IncomingConnectionTCP
 * (used to read from the socket). The OutgoingConnection has a BlockingQueue; messages to be sent should be placed in
 * this queue. The IncomingConnectionTCP relays messages to the ServerThread's queue.
 */
public abstract class PeerConnection {
    // Data
    private final String name;
    private boolean wasOutgoing;
    private HostPort localHostPort;
    private HostPort hostPort;
    protected PeerState state;

    // Objects needed
    private OutgoingConnection outConn;
    private final ServerMain server;

    public void forceIncoming() {
        wasOutgoing = false;
    }

    // Returns the current state of the PeerConnection
    protected PeerState getState() {
        synchronized (this) {
            return state;
        }
    }

    public boolean needsRequest() {
        return getState() == PeerState.WAIT_FOR_REQUEST;
    }
    public boolean needsResponse() {
        return getState() == PeerState.WAIT_FOR_RESPONSE;
    }
    public boolean isActive() {
        return getState() == PeerState.ACTIVE;
    }

    // Activate the peer connection after a handshake is complete.
    // Optionally, allow the port to be updated.
    public void activateDefault() {
        activate(localHostPort);
    }

    public void activate(HostPort hostPort) {
        this.hostPort = hostPort;
        activateInternal();
    }

    private void activateInternal() {
        synchronized (this) {
            // Don't do anything if we're not waiting to be activated.
            if (state == PeerState.WAIT_FOR_RESPONSE || state == PeerState.WAIT_FOR_REQUEST) {
                ServerMain.log.info("Activating " + getForeignName());
                state = PeerState.ACTIVE;
            }
        }
        KnownPeerTracker.addAddress(hostPort);
    }

    private void deactivate() {
        synchronized (this) {
            state = PeerState.INACTIVE;
        }
    }

    // Returns true if this peer was an outgoing connection (i.e. was in the peers list)
    public boolean getOutgoing() {
        return wasOutgoing;
    }

    /**
     * Returns a HostPort object representing the *local* information about this socket.
     * May not be the correct host and port to reply to.
     * @return the local host and port
     */
    public HostPort getLocalHostPort() {
        return localHostPort;
    }

    /**
     * Returns a HostPort object representing the actual host and port of the connected peer,
     * to the best of our knowledge.
     * @return the host and port
     */
    public HostPort getHostPort() {
        return hostPort;
    }

    public String getName() {
        return name;
    }

    public String getForeignName() {
        return getName() + "-" + hostPort;
    }

    PeerConnection(String name, ServerMain server, boolean outgoing, String host, int port, OutgoingConnection outConn) {
        ServerMain.log.info("Peer created: " + name + " @ " + host + ":" + port);
        this.name = name;
        this.server = server;
        // If we're outgoing, then we sent a handshake request
        this.state = outgoing ? PeerState.WAIT_FOR_RESPONSE : PeerState.WAIT_FOR_REQUEST;
        this.outConn = outConn;
        wasOutgoing = outgoing;
        localHostPort = new HostPort(host, port);
        hostPort = localHostPort;

        outConn.start();

        if (outgoing) {
            ServerMain.log.info(getForeignName() + ": Sending handshake request");
            sendMessageInternal(new HandshakeRequest(server.getHostPort()));
        }
    }

    // Closes this peer.
    public final void close() {
        synchronized (this) {
            if (state == PeerState.CLOSED) {
                return;
            }
            state = PeerState.CLOSED;
        }
        ServerMain.log.warning("Connection to peer `" + getForeignName() + "` closed.");
        outConn.deactivate();
        server.getConnection().closeConnection(this);
        closeInternal();
    }

    protected abstract void closeInternal();

    /**
     * Send a message to this peer. Validates the state first.
     */
    public void sendMessage(Message message) {
        sendMessage(message, () -> {});
    }

    /**
     * Send a message to this peer, and then close the connection.
     */
    public synchronized void sendMessageAndClose(Message message) {
        sendMessage(message, this::close);
        deactivate();
    }

    /**
     * Implementation of the actual sendMessage code, to allow default parameters.
     */
    private void sendMessage(Message message, Runnable onSent) {
        activateInternal();
        sendMessageInternal(message, onSent);
    }

    /**
     * Send a message to this peer, regardless of state.
     */
    protected void sendMessageInternal(Message message) {
        sendMessageInternal(message, () -> {});
    }

    /**
     * Send a message to this peer, regardless of state. Allows a function to run after the message is sent.
     */
    protected void sendMessageInternal(Message message, Runnable onSent) {
        synchronized (this) {
            if (state == PeerState.CLOSED) {
                return;
            }
        }
        message.setFriendlyName(name + "-" + server.getHostPort());
        ServerMain.log.info(getForeignName() + " sent: " + message.getCommand());
        outConn.addMessage(new OutgoingMessage(message.networkEncode(), onSent));
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

    @Override
    public String toString() {
        String address = hostPort.asAddress();
        String alias = hostPort.asAliasedAddress();
        if (!address.equals(alias)) {
            address += " (" + alias + ")";
        }

        return name + " @ " + address;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof PeerConnection) {
            PeerConnection rhs = (PeerConnection) other;
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

    public String networkEncoded() {
        return message.trim() + "\n";
    }
}

enum PeerState {
    WAIT_FOR_REQUEST,
    WAIT_FOR_RESPONSE,
    ACTIVE,
    CLOSED,
    INACTIVE
}