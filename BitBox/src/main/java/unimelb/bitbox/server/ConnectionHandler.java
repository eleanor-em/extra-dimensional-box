package unimelb.bitbox.server;

import functional.algebraic.Maybe;
import unimelb.bitbox.messages.ConnectionRefused;
import unimelb.bitbox.messages.HandshakeRequest;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.peers.PeerType;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.IJSONData;
import unimelb.bitbox.util.network.JSONDocument;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Handles the connections to the various peers.
 *
 * @author Eleanor McMurtry
 * @author Andrea Law
 */
public class ConnectionHandler implements IJSONData {
    // Settings
    private static final int PEER_RETRY_TIME = 60;
    private static final String DEFAULT_NAME = "Anonymous";
    private static final CfgValue<Integer> maxIncomingConnections = CfgValue.createInt("maximumConnections");
    private final int port;

    // Objects for use by this class
    private ServerSocket socket = null;
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
        try {
            socket.close();
        } catch (IOException e) {
            PeerServer.log().severe("Failed closing socket");
            e.printStackTrace();
        }

        executor.shutdownNow();
        closeAllConnections();
    }

    private void addPeerAddress(String address) {
        HostPort.fromAddress(address)
                .match(peerHostPort -> {
                           PeerServer.log().fine("Adding address " + address + " to connection list");
                           addPeerAddress(peerHostPort);
                       }, ignored -> PeerServer.log().warning("Tried to add invalid address `" + address + "`"));
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

    private boolean hasPeer(HostPort hostPort) {
        return peers.stream().anyMatch(peer -> peer.matches(hostPort));
    }

    Maybe<Peer> getPeer(HostPort hostPort) {
        return Maybe.of(peers.stream()
                    .filter(peer -> peer.matches(hostPort))
                    .findFirst());
    }

    List<Peer> getActivePeers() {
        return peers.stream()
                .filter(Peer::isActive)
                .collect(Collectors.toList());
    }

    private void addPeer(Peer peer) {
        peers.add(peer);
    }

    private boolean canStorePeer() {
        return getActivePeers().size() < maxIncomingConnections.get();
    }

    private String getAnyName() {
        return Optional.ofNullable(names.poll())
                .orElse(DEFAULT_NAME);
    }


    private void acceptConnections() {
        // Need to set and then await in case there was already a socket created
        PeerServer.log().fine("Listening on port " + port);

        while (!socket.isClosed()) {
            try {
                Socket clientSocket = socket.accept();
                PeerServer.log().fine("Accepted connection: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                // check we have room for more peers
                // (only count incoming connections)
                if (canStorePeer()) {
                    final Peer peer = new Peer(getAnyName(), clientSocket, PeerType.INCOMING);
                    addPeer(peer);
                    PeerServer.log().fine("Connected to peer " + peer);
                } else {
                    // if not, write a CONNECTION_REFUSED message and close the connection
                    try (var writer = new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8);
                         var out = new BufferedWriter(writer)) {
                        out.write(new ConnectionRefused("connection list full").networkEncode());
                        out.flush();
                        PeerServer.log().fine("Sending CONNECTION_REFUSED");
                    } catch (IOException e) {
                        e.printStackTrace();
                        PeerServer.log().warning("Failed writing CONNECTION_REFUSED");
                    }
                    clientSocket.close();
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                PeerServer.log().warning("Failed connecting to peer");
                e.printStackTrace();
            }
        }
        PeerServer.log().fine("No longer listening on port " + this.port);
    }

    private Maybe<Peer> tryPeer(HostPort peerHostPort) {
        if (hasPeer(peerHostPort)) {
            return Maybe.nothing();
        }
        addPeerAddress(peerHostPort);

        try {
            Socket socket = new Socket(peerHostPort.hostname, peerHostPort.port);

            // find a name
            String name = getAnyName();
            Peer peer = new Peer(name, socket, PeerType.OUTGOING);
            peer.sendMessage(new HandshakeRequest());
            addPeer(peer);
            PeerServer.log().fine("Connected to peer " + name + " @ " + peerHostPort);
            return Maybe.just(peer);
        } catch (IOException e) {
            PeerServer.log().warning("Connection to peer `" + peerHostPort + "` failed: " + e.getMessage());
        }

        return Maybe.nothing();
    }

    private void acceptConnectionsPersistent() {
        try {
            socket = new ServerSocket(port);
            acceptConnections();
        } catch (Exception e) {
            PeerServer.log().severe("Accepting connections failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (active.get()) {
                PeerServer.log().fine("Restarting accept thread");
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
}
