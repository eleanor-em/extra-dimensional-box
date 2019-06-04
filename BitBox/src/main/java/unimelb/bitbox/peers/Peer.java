package unimelb.bitbox.peers;

import unimelb.bitbox.messages.Message;
import unimelb.bitbox.messages.ReceivedMessage;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.network.HostPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private final AtomicReference<PeerState> state = new AtomicReference<>();
    private final OutgoingConnection outConn;
    private final List<Runnable> onClose = Collections.synchronizedList(new ArrayList<>());

    // Handles outgoing/incoming connection threads
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Set<Future<?>> threads = ConcurrentHashMap.newKeySet();
    void submit(Runnable service) {
        threads.add(executor.submit(service));
        onClose.add(() -> threads.forEach(t -> t.cancel(true)));
    }

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
            // Add to our tracker
            KnownPeerTracker.addAddress(localHostPort, hostPort);
            KnownPeerTracker.notifyPeerCount(PeerServer.getPeerCount());

            // Trigger synchronisation
            PeerServer.log().info("Activating " + getForeignName());
            PeerServer.synchroniseFiles();
        }
    }


    /**
     * Returns a HostPort object representing the actual host and port of the connected peer,
     * to the best of our knowledge.
     * @return the host and port
     */
    public HostPort getHostPort() {
        return hostPort;
    }

    /**
     * Tests whether the peer's information matches the given HostPort.
     */
    public boolean matches(HostPort peerHostPort) {
        return localHostPort.fuzzyEquals(peerHostPort) || localHostPort.fuzzyEquals(peerHostPort);
    }
    /**
     * @return the plain name (e.g. Alice, Bob, Carol) of this peer
     */
    public String getName() {
        return name;
    }

    /**
     * @return the name of the peer, plus its connected address
     */
    public String getForeignName() {
        return name + "-" + hostPort;
    }

    /**
     * Construct a Peer.
     * @param name      the name to attach to this peer
     * @param outgoing  whether the peer was an otugoing connection
     * @param host      the hostname of the peer
     * @param port      the port of the peer
     */
    Peer(String name, boolean outgoing, String host, int port, OutgoingConnection outConn) {
        PeerServer.log().info("Peer created: " + name + " @ " + host + ":" + port);
        this.name = name;
        wasOutgoing = outgoing;

        localHostPort = new HostPort(host, port);
        hostPort = localHostPort;

        state.set(outgoing ? PeerState.WAIT_FOR_RESPONSE : PeerState.WAIT_FOR_REQUEST);
        this.outConn = outConn;
        submit(outConn);
    }

    /**
     * Closes this peer.
     */
    public final void close() {
        if (state.get() == PeerState.CLOSED) {
            return;
        }
        state.set(PeerState.CLOSED);

        PeerServer.log().warning("Connection to peer `" + getForeignName() + "` closed.");
        PeerServer.getConnection().closeConnection(this);
        synchronized (onClose) {
            onClose.forEach(Runnable::run);
        }
        closeInternal();
    }

    /**
     * An action to perform when the peer is closed.
     */
    protected void closeInternal() {}

    /**
     * Send a message to this peer.
     */
    public final void sendMessage(Message message) {
        sendMessage(message, () -> {});
    }

    /**
     * Send a message to this peer, then close the peer.
     */
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
        PeerServer.log().info(getForeignName() + " sent: " + message.getCommand());
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

    /**
     * Called when a request has been sent to this peer.
     */
    protected void requestSent(Message request) {}

    /**
     * Called when a response has been received from this peer.
     */
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

    public OutgoingMessage(String message, Runnable onSent) {
        this.message = message;
        this.onSent = onSent;
    }

    public String networkEncoded() {
        return message.trim() + "\n";
    }
}