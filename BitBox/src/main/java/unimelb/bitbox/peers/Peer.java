package unimelb.bitbox.peers;

import functional.algebraic.Maybe;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.messages.MessageType;
import unimelb.bitbox.messages.ReceivedMessage;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.network.HostPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * States a Peer can be in.
 *
 * @author Eleanor McMurtry
 */
enum PeerState {
    WAIT_FOR_REQUEST,
    WAIT_FOR_RESPONSE,
    ACTIVE,
    CLOSED
}

/**
 * A Peer is a combination of an OutgoingConnection (used to write to the socket) and an IncomingConnectionTCP
 * (used to read from the socket). The OutgoingConnection has a BlockingQueue; messages to be sent should be placed in
 * this queue. The IncomingConnectionTCP relays messages to the ServerThread's queue.
 *
 * @author Eleanor McMurtry
 * @author Andrea Law
 */
public class Peer {
    // Data
    private final Socket socket;
    private final String name;
    private final HostPort localHostPort;
    private HostPort hostPort;

    // Objects needed for work
    private final AtomicReference<PeerState> state = new AtomicReference<>();
    private final OutgoingConnection outConn;
    private final List<Runnable> onClose = Collections.synchronizedList(new ArrayList<>());

    // Handles outgoing/incoming connection threads
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Set<Future<?>> threads = ConcurrentHashMap.newKeySet();
    private void submit(Runnable service) {
        threads.add(executor.submit(service));
        onClose.add(() -> threads.forEach(t -> t.cancel(true)));
    }

    void addCloseTask(Runnable task) {
        onClose.add(task);
    }

    public boolean needsResponse() {
        return state.get() == PeerState.WAIT_FOR_RESPONSE;
    }
    public boolean isActive() {
        return state.get() == PeerState.ACTIVE;
    }

    // Activate the peer connection after a handshake is complete.
    public void activate(HostPort hostPort) {
        // Update host information
        this.hostPort = hostPort;
        activateInternal();
    }

    private void activateInternal() {
        // Don't do anything if we're not waiting to be activated.
        if (state.compareAndSet(PeerState.WAIT_FOR_RESPONSE, PeerState.ACTIVE)
                || state.compareAndSet(PeerState.WAIT_FOR_REQUEST, PeerState.ACTIVE)) {
            PeerServer.log().fine("Activating " + getForeignName());

            // Add to our tracker
            KnownPeerTracker.addAddress(localHostPort, hostPort);
            KnownPeerTracker.notifyPeerCount(PeerServer.getPeerCount());
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
        return localHostPort.fuzzyEquals(peerHostPort) || hostPort.fuzzyEquals(peerHostPort);
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
     * @param type      whether the peer was an otugoing connection
     */
    public Peer(String name, Socket socket, PeerType type) {
        var host = socket.getInetAddress().getHostAddress();
        var port = socket.getPort();
        this.socket = socket;
        outConn = new OutgoingConnection(socket);
        PeerServer.log().fine("Peer created: " + name + " @ " + host + ":" + port);
        this.name = name;

        localHostPort = new HostPort(host, port);
        hostPort = localHostPort;

        state.set(type == PeerType.OUTGOING ? PeerState.WAIT_FOR_RESPONSE : PeerState.WAIT_FOR_REQUEST);
        submit(outConn);

        submit(this::receiveMessages);
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
        PeerServer.connection().closeConnection(this);
        synchronized (onClose) {
            onClose.forEach(Runnable::run);
        }

        try {
            socket.close();
        } catch (IOException e) {
            PeerServer.log().severe("Error closing socket: " + e.getMessage());
        }
    }

    /**
     * Send a message to this peer.
     */
    public final void sendMessage(Message message) {
        sendMessage(message, () -> {});
    }

    /**
     * Send a message to this peer, then close the peer.
     */
    public final synchronized void sendMessageAndClose(Message message) {
        sendMessage(message, this::close);
    }

    private void sendMessage(Message message, Runnable onSent) {
        sendMessageInternal(message, onSent);
    }

    private void sendMessageInternal(Message message, Runnable onSent) {
        if (state.get() == PeerState.CLOSED) {
            return;
        }

        message.setFriendlyName(name + "-" + PeerServer.hostPort());
        outConn.addMessage(new OutgoingMessage(message.networkEncode(), onSent));
        PeerServer.log().fine(getForeignName() + " sent: " + message.toString());
    }

    private void receiveMessages() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while (Maybe.of(message = in.readLine()).isJust()) {
                PeerServer.enqueueMessage(new ReceivedMessage(message, this));
            }
        } catch (IOException e) {
            if (state.get() != PeerState.CLOSED) {
                PeerServer.log().severe("Error reading from socket: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    /**
     * This method is called when a message has been received from this peer and successfully parsed.
     */
    public final void notify(Message message) {
        if (!message.isRequest()) {
            // If we got a message other than a handshake response, make sure we're active
            if (message.getCommand().orElse(null) != MessageType.HANDSHAKE_RESPONSE) {
                activateInternal();
            }
        }
    }

    @Override
    public String toString() {
        String address = hostPort.asAddress();
        String alias = hostPort.asAliasedAddress();
        if (!address.equals(alias)) {
            address += " (" + alias + ")";
        }

        return name + " @ " + address;
    }
}


/**
 * A class to pair a message with a function to run when the message is sent.
 */
class OutgoingMessage {
    public final String message;
    public final Runnable onSent;

    OutgoingMessage(String message, Runnable onSent) {
        this.message = message;
        this.onSent = onSent;
    }
}