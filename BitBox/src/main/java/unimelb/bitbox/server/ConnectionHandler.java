package unimelb.bitbox.server;

import unimelb.bitbox.messages.Message;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.util.concurrency.DelayedInitialiser;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.functional.algebraic.Maybe;
import unimelb.bitbox.util.network.*;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public abstract class ConnectionHandler implements IJSONData {
    // Settings
    private static final int PEER_RETRY_TIME = 60;
    private static final String DEFAULT_NAME = "Anonymous";
    private static final CfgValue<Integer> maxIncomingConnections = CfgValue.createInt("maximumIncommingConnections");
    final int port;

    // Objects for use by this class
    private final DelayedInitialiser<ISocket> socket = new DelayedInitialiser<>();
    private final Set<HostPort> peerAddresses = ConcurrentHashMap.newKeySet();
    private final Queue<String> names = new ConcurrentLinkedQueue<>();

    // Adding and removing peers is uncommon compared to iterating over peers.
    // Use CopyOnWrite for synchronization efficiency
    private final List<Peer> peers = new CopyOnWriteArrayList<>();

    // Threading
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    ConnectionHandler() {
        port = PeerServer.hostPort().port;
        createNames();

        executor.submit(this::connectToPeers);
        executor.submit(this::acceptConnectionsPersistent);
    }

    void deactivate() {
        active.set(false);
        socket.get().consume(wrapper -> {
            try {
                wrapper.close();
            } catch (IOException e) {
                PeerServer.log().severe("Failed closing socket");
                e.printStackTrace();
            }
        });

        executor.shutdownNow();
        closeAllConnections();
    }

    private void addPeerAddress(String address) {
        HostPort.fromAddress(address)
                .match(ignored -> PeerServer.log().warning("Tried to add invalid address `" + address + "`"),
                       peerHostPort -> {
                           PeerServer.log().fine("Adding address " + address + " to connection list");
                           addPeerAddress(peerHostPort);
                       });
    }
    void addPeerAddress(HostPort peerHostPort) {
        peerAddresses.add(peerHostPort);
    }

    void addPeerAddressAll(String[] addresses) {
        for (String address : addresses) {
            addPeerAddress(address);
        }
    }

    void retryPeers() {
        // Remove all peers that successfully connect.
        peerAddresses.removeIf(addr -> tryPeer(addr).isJust());
    }

    /**
     * Returns a JSONDocument containing a field "peers" with a list of all active peers' HostPorts.
     */
    public JSONDocument toJSON() {
        JSONDocument doc = new JSONDocument();
        doc.append("peers", peers.stream()
                                .filter(Peer::isActive)
                                .map(Peer::getHostPort)
                                .collect(Collectors.toList()));
        return doc;
    }

    public boolean clientTryPeer(HostPort hostPort){
        if (canStorePeer()) {
            return tryPeer(hostPort).map(peer -> {
                peer.forceIncoming();
                return true;
            }).fromMaybe(false);
        }
        return false;
    }

    void broadcastMessage(Message message) {
        getActivePeers().forEach(peer -> peer.sendMessage(message));
    }

    public void closeConnection(Peer peer) {
        if (peers.contains(peer)) {
            peers.remove(peer);
            peer.close();
            PeerServer.log().fine("Removing " + peer.getForeignName() + " from peer list");

            // return the plain name to the queue, if it's not the default
            String plainName = peer.getName();
            if (!plainName.equals(DEFAULT_NAME)) {
                names.add(plainName);
            }
        }
    }

    private void closeAllConnections() {
        peers.forEach(this::closeConnection);
    }

    void setSocket(ISocket value) {
        socket.set(value);
    }

    DatagramSocket awaitUDPSocket() {
        try {
            ISocket value = socket.await();
            assert value instanceof UDPSocket;
            return ((UDPSocket) value).get();
        } catch (InterruptedException e) {
            PeerServer.log().warning("Thread interrupted while waiting for socket: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    ServerSocket awaitTCPSocket() {
        try {
            ISocket value = socket.await();
            assert value instanceof TCPSocket;
            return ((TCPSocket) value).get();
        } catch (InterruptedException e) {
            PeerServer.log().warning("Thread interrupted while waiting for socket: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    boolean hasPeer(HostPort hostPort) {
        return peers.stream()
                    .anyMatch(peer -> peer.matches(hostPort));
    }

    public Maybe<Peer> getPeer(HostPort hostPort) {
        return Maybe.of(peers.stream()
                    .filter(peer -> peer.matches(hostPort))
                    .findFirst());
    }

    public List<Peer> getActivePeers() {
        return peers.stream()
                .filter(Peer::isActive)
                .collect(Collectors.toList());
    }

    void addPeer(Peer peer) {
        peers.add(peer);
    }

    boolean canStorePeer() {
        return getIncomingPeerCount() < maxIncomingConnections.get();
    }

    String getAnyName() {
        return Optional.ofNullable(names.poll())
                .orElse(DEFAULT_NAME);
    }

    abstract void acceptConnections() throws IOException;
    abstract Maybe<Peer> tryPeer(HostPort address);

    private void acceptConnectionsPersistent() {
        try {
            acceptConnections();
        } catch (Exception e) {
            PeerServer.log().severe("Accepting connections failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (active.get()) {
                PeerServer.log().fine("Restarting accept thread");
                if (socket.get().map(ISocket::isClosed).fromMaybe(false)) {
                    socket.reset();
                }
                executor.submit(this::acceptConnectionsPersistent);
            }
        }
    }

    private void connectToPeers() {
        try {
            while (true) {
                retryPeers();
                Thread.sleep(PEER_RETRY_TIME * 1000);
            }
        } catch (InterruptedException ignored) {
            // It's expected that we might get interrupted here.
        } catch (Exception e) {
            PeerServer.log().severe("Retrying peers failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (active.get()) {
                PeerServer.log().fine("Restarting peer retry thread");
                executor.submit(this::connectToPeers);
            }
        }
    }

    private void createNames() {
        names.add("Alice");
        names.add("Bob");
        names.add("Carol");
        names.add("Declan");
        names.add("Eve");
        names.add("Fred");
        names.add("Gerald");
        names.add("Hannah");
        names.add("Imogen");
        names.add("Jacinta");
        names.add("Kayleigh");
        names.add("Lauren");
        names.add("Maddy");
        names.add("Nicole");
        names.add("Opal");
        names.add("Percival");
        names.add("Quinn");
        names.add("Ryan");
        names.add("Steven");
        names.add("Theodore");
        names.add("Ulla");
        names.add("Violet");
        names.add("William");
        names.add("Xinyu");
        names.add("Yasmin");
        names.add("Zuzanna");
    }

    private int getIncomingPeerCount() {
        synchronized (peers) {
            return (int)peers.stream()
                    .filter(peer -> !peer.getOutgoing())
                    .count();
        }
    }
}
