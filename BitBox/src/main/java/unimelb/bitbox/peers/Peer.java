package unimelb.bitbox.peers;

import unimelb.bitbox.messages.HandshakeRequest;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.messages.ReceivedMessage;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.functional.combinator.Combinators;
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
    private final AtomicReference<PeerState> state = new AtomicReference<>();

    // Objects needed for work
    private final OutgoingConnection outConn;
    private final List<Runnable> onClose = Collections.synchronizedList(new ArrayList<>());

    public void forceIncoming() {
        wasOutgoing = false;
    }
    public void addCloseTask(Runnable task) {
        onClose.add(task);
    }

    public boolean needsResponse() {
        return state.get() == PeerState.WAIT_FOR_RESPONSE;
    }
    public boolean isClosed() {
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
            KnownPeerTracker.addAddress(localHostPort, hostPort);
            KnownPeerTracker.notifyPeerCount(PeerServer.getPeerCount());
            PeerServer.logInfo("Activating " + getForeignName());
            PeerServer.synchroniseFiles();
        }
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

    Peer(String name, boolean outgoing, String host, int port, OutgoingConnection outConn) {
        PeerServer.logInfo("Peer created: " + name + " @ " + host + ":" + port);
        this.name = name;
        this.outConn = outConn;
        wasOutgoing = outgoing;
        localHostPort = new HostPort(host, port);
        hostPort = localHostPort;
        this.state.set(outgoing ? PeerState.WAIT_FOR_RESPONSE : PeerState.WAIT_FOR_REQUEST);

        outConn.start();
        if (outgoing) {
            PeerServer.logInfo(getForeignName() + ": Sending handshake request");
            outConn.addMessage(new OutgoingMessage(new HandshakeRequest(PeerServer.getHostPort()).networkEncode()));
        }
    }

    // Closes this peer.
    public final void close() {
        if (state.get() == PeerState.CLOSED) {
            return;
        }
        state.set(PeerState.CLOSED);

        PeerServer.logWarning("Connection to peer `" + getForeignName() + "` closed.");
        outConn.deactivate();
        PeerServer.getConnection().closeConnection(this);
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

    private void sendMessageInternal(Message message, Runnable onSent) {
        if (state.get() == PeerState.CLOSED) {
            return;
        }

        if (message.isRequest()) {
            requestSent(message);
        }

        message.setFriendlyName(name + "-" + PeerServer.getHostPort());
        PeerServer.logInfo(getForeignName() + " sent: " + message.getCommand());
        outConn.addMessage(new OutgoingMessage(message.networkEncode(), onSent));
    }

    /**
     * This method is called when a message is received from this peer.
     */
    public void receiveMessage(String message) {
        PeerServer.enqueueMessage(new ReceivedMessage(message, this));
    }

    /**
     * This method is called when a message has been received from this peer and successfully parsed.
     */
    public final void notify(Message message) {
        if (!message.isRequest()) {
            responseReceived(message);
        }
    }

    protected void requestSent(Message request) {}

    protected void responseReceived(Message response) {}

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

    public OutgoingMessage(String message) {
        this.message = message;
        this.onSent = Combinators::noop;
    }
    public OutgoingMessage(String message, Runnable onSent) {
        this.message = message;
        this.onSent = onSent;
    }

    public String networkEncoded() {
        return message.trim() + "\n";
    }
}