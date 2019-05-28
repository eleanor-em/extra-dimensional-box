package unimelb.bitbox.peers;

import unimelb.bitbox.messages.HandshakeRequest;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.messages.ReceivedMessage;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.network.HostPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

enum PeerState {
    WAIT_FOR_REQUEST,
    WAIT_FOR_RESPONSE,
    ACTIVE,
    CLOSED,
    INACTIVE
}

/**
 * A Peer is a combination of an OutgoingConnection (used to write to the socket) and an IncomingConnectionTCP
 * (used to read from the socket). The OutgoingConnection has a BlockingQueue; messages to be sent should be placed in
 * this queue. The IncomingConnectionTCP relays messages to the ServerThread's queue.
 */
public abstract class Peer {
    // Data
    private final String name;
    private boolean wasOutgoing;
    private final HostPort localHostPort;
    private HostPort hostPort;

    // Objects needed for work
    private final OutgoingConnection outConn;
    private final PeerServer server;
    private final List<Runnable> onClose = Collections.synchronizedList(new ArrayList<>());
    protected AtomicReference<PeerState> state = new AtomicReference<>();

    public void forceIncoming() {
        wasOutgoing = false;
    }
    public void addCloseTask(Runnable task) {
        onClose.add(task);
    }

    // State queries
    public boolean needsRequest() {
        return state.get() == PeerState.WAIT_FOR_REQUEST;
    }
    public boolean needsResponse() {
        return state.get() == PeerState.WAIT_FOR_RESPONSE;
    }
    protected boolean isClosed() {
        return state.get() == PeerState.CLOSED;
    }
    public boolean isActive() {
        return state.get() == PeerState.ACTIVE;
    }
    public boolean getOutgoing() {
        return wasOutgoing;
    }

    // Activate the peer connection after a handshake is complete.
    public void activate(HostPort hostPort) {
        this.hostPort = hostPort;
        // Don't do anything if we're not waiting to be activated.
        if (state.compareAndSet(PeerState.WAIT_FOR_RESPONSE, PeerState.ACTIVE)
         || state.compareAndSet(PeerState.WAIT_FOR_REQUEST, PeerState.ACTIVE)) {
            PeerServer.log.info("Activating " + getForeignName());
        }
        KnownPeerTracker.addAddress(hostPort);
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
        return name + "-" + hostPort;
    }

    Peer(String name, PeerServer server, boolean outgoing, String host, int port, OutgoingConnection outConn) {
        PeerServer.log.info("Peer created: " + name + " @ " + host + ":" + port);
        this.name = name;
        this.server = server;
        this.outConn = outConn;
        wasOutgoing = outgoing;
        localHostPort = new HostPort(host, port);
        hostPort = localHostPort;

        if (outgoing) {
            PeerServer.log.info(getForeignName() + ": Sending handshake request");
            sendMessageInternal(new HandshakeRequest(server.getHostPort()));
        }
        this.state.set(outgoing ? PeerState.WAIT_FOR_RESPONSE : PeerState.WAIT_FOR_REQUEST);

        outConn.start();
    }

    // Closes this peer.
    public final void close() {
        if (state.get() == PeerState.CLOSED) {
            return;
        }
        state.set(PeerState.CLOSED);

        PeerServer.log.warning("Connection to peer `" + getForeignName() + "` closed.");
        outConn.deactivate();
        server.getConnection().closeConnection(this);
        synchronized (onClose) {
            onClose.forEach(Runnable::run);
        }
        closeInternal();
    }
    protected void closeInternal() {}

    public final void sendMessage(Message message) {
        sendMessage(message, () -> {});
    }
    public synchronized final void sendMessageAndClose(Message message) {
        sendMessage(message, this::close);
    }

    private void sendMessage(Message message, Runnable onSent) {
        sendMessageInternal(message, onSent);
    }

    protected void sendMessageInternal(Message message) {
        sendMessageInternal(message, () -> {});
    }

    protected void sendMessageInternal(Message message, Runnable onSent) {
        if (state.get() == PeerState.CLOSED) {
            return;
        }

        message.setFriendlyName(name + "-" + server.getHostPort());
        PeerServer.log.info(getForeignName() + " sent: " + message.getCommand());
        outConn.addMessage(new OutgoingMessage(message.networkEncode(), onSent));
    }

    /**
     * This method is called when a message is received from this peer.
     */
    public void receiveMessage(String message) {
        server.enqueueMessage(new ReceivedMessage(message, this));
    }

    /**
     * This method is called when a message has been received from this peer and successfully parsed.
     */
    public final void notify(Message message) {
        notifyInternal(message);
    }

    protected void notifyInternal(Message message) {}

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
        return other instanceof Peer && ((Peer) other).hostPort.fuzzyEquals(hostPort)
                                     && ((Peer) other).name.equals(name);
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