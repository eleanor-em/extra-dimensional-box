package unimelb.bitbox.server.connections;

import unimelb.bitbox.peers.PeerConnection;
import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.SocketWrapper;
import unimelb.bitbox.util.network.TCPSocket;
import unimelb.bitbox.util.network.UDPSocket;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class ConnectionHandler {
    // Settings
    private static final int PEER_RETRY_TIME = 60;
    private static final String DEFAULT_NAME = "Anonymous";
    private static final CfgValue<Integer> maxIncomingConnections = CfgValue.createInt("maximumIncommingConnections");
    protected final ServerMain server;
    protected final int port;

    // Socket control
    private CountDownLatch socketReady = new CountDownLatch(1);
    private AtomicReference<SocketWrapper> socketContainer = new AtomicReference<>();

    // Objects for use by this class
    private final List<PeerConnection> peers = Collections.synchronizedList(new ArrayList<>());
    private final Set<HostPort> peerAddresses = ConcurrentHashMap.newKeySet();
    private final Queue<String> names = new ConcurrentLinkedQueue<>();

    public ConnectionHandler(ServerMain server) {
        this.server = server;
        port = ServerMain.getHostPort().port;
        createNames();
    }

    public void connectToPeers() {
        try {
            while (true) {
                retryPeers();
                Thread.sleep(PEER_RETRY_TIME * 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ServerMain.log.severe("Restarting peer connection thread");
            new Thread(this::connectToPeers).start();
        }
    }

    protected void setSocket(SocketWrapper socket) {
        // Check that the socketContainer hasn't already been set.
        if (this.socketContainer.compareAndSet(null, socket)) {
            socketReady.countDown();
        }
    }

    protected Optional<DatagramSocket> getSocketAsUDP() {
        try {
            socketReady.await();
            SocketWrapper socket = socketContainer.get();
            if (socket instanceof UDPSocket) {
                return Optional.of(((UDPSocket) socket).get());
            }
        } catch (InterruptedException e) {
            ServerMain.log.warning("Thread interrupted while waiting for socket: " + e.getMessage());
        }
        return Optional.empty();
    }

    protected Optional<ServerSocket> getSocketAsTCP() {
        try {
            socketReady.await();
            SocketWrapper socket = socketContainer.get();
            if (socket instanceof TCPSocket) {
                return Optional.of(((TCPSocket)socket).get());
            }
        } catch (InterruptedException e) {
            ServerMain.log.warning("Thread interrupted while waiting for socket: " + e.getMessage());
        }
        return Optional.empty();
    }

    protected boolean hasPeer(HostPort hostPort) {
        return getPeer(hostPort) != null;
    }

    protected PeerConnection getPeer(HostPort hostPort) {
        synchronized (peers) {
            for (PeerConnection peer : peers) {
                HostPort peerLocalHP = peer.getLocalHostPort();
                HostPort peerHP = peer.getHostPort();

                if (peerLocalHP.fuzzyEquals(hostPort) || peerHP.fuzzyEquals(hostPort)) {
                    return peer;
                }
            }
        }
        return null;
    }

    protected List<PeerConnection> getActivePeers() {
        synchronized (peers) {
            return peers.stream()
                    .filter(PeerConnection::isActive)
                    .collect(Collectors.toList());
        }
    }

    protected void addPeer(PeerConnection peer) {
        peers.add(peer);
    }

    protected boolean canStorePeer() {
        return getIncomingPeerCount() < maxIncomingConnections.get();
    }

    public String getAnyName() {
        return Optional.ofNullable(names.poll())
                .orElse(DEFAULT_NAME);
    }

    abstract void acceptConnections() throws IOException;
    abstract PeerConnection tryPeer(HostPort address);

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

    private void retryPeers() {
        // Remove all peers that successfully connect.
        peerAddresses.removeIf(addr -> tryPeer(addr) != null);
    }

    private int getIncomingPeerCount() {
        synchronized (peers) {
            return (int)peers.stream()
                    .filter(peer -> !peer.getOutgoing())
                    .count();
        }
    }
}
