package unimelb.bitbox.server.connections;

import unimelb.bitbox.messages.Message;
import unimelb.bitbox.peers.PeerConnection;
import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.util.concurrency.DelayedInitialiser;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.network.*;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public abstract class ConnectionHandler {
    // Settings
    private static final int PEER_RETRY_TIME = 60;
    private static final String DEFAULT_NAME = "Anonymous";
    private static final CfgValue<Integer> maxIncomingConnections = CfgValue.createInt("maximumIncommingConnections");
    protected final ServerMain server;
    protected final int port;

    // Objects for use by this class
    private final DelayedInitialiser<SocketWrapper> socket = new DelayedInitialiser<>();
    private final List<PeerConnection> peers = Collections.synchronizedList(new ArrayList<>());
    private final Set<HostPort> peerAddresses = ConcurrentHashMap.newKeySet();
    private final Queue<String> names = new ConcurrentLinkedQueue<>();

    // Threading
    private Thread connectThread;
    private Thread acceptThread;
    private AtomicBoolean active = new AtomicBoolean(true);

    public ConnectionHandler(ServerMain server) {
        this.server = server;
        port = server.getHostPort().port;
        createNames();

        connectThread = new Thread(this::connectToPeers);
        connectThread.start();
        acceptThread = new Thread(this::acceptConnectionsPersistent);
        acceptThread.start();
    }

    public void deactivate() {
        active.set(false);
        connectThread.interrupt();
        acceptThread.interrupt();
        closeAllConnections();
    }

    public void addPeerAddress(String address) {
        try {
            addPeerAddress(HostPort.fromAddress(address));
            ServerMain.log.info("Adding address " + address + " to connection list");
        } catch (HostPortParseException e) {
            ServerMain.log.warning("Tried to add invalid address `" + address + "`");
        }
    }
    public void addPeerAddress(HostPort peerHostPort) {
        peerAddresses.add(peerHostPort);
    }

    public void addPeerAddressAll(String[] addresses) {
        for (String address : addresses) {
            addPeerAddress(address);
        }
    }

    public synchronized void retryPeers() {
        // Remove all peers that successfully connect.
        peerAddresses.removeIf(addr -> tryPeer(addr) != null);
    }

    public Collection<HostPort> getOutgoingAddresses() {
        synchronized (peers) {
            return peers.stream()
                    .filter(PeerConnection::getOutgoing)
                    .map(PeerConnection::getHostPort)
                    .collect(Collectors.toList());
        }
    }

    // TODO: Make this wait for the actual connection to exist
    public boolean clientTryPeer(HostPort hostPort){
        if (canStorePeer()) {
            PeerConnection peer = tryPeer(hostPort);
            if (peer != null) {
                peer.forceIncoming();
                return true;
            }
        }
        return false;
    }

    public void broadcastMessage(Message message) {
        getActivePeers().forEach(peer -> peer.sendMessage(message));
    }


    public void closeConnection(PeerConnection peer) {
        closeConnectionInternal(peer);
        synchronized (peers) {
            peers.remove(peer);
        }
    }
    public void closeAllConnections() {
        // TODO: Problem here is peer.close() will call closeConnection, not closeConnectionInternal.
        synchronized (peers) {
            peers.forEach(this::closeConnectionInternal);
            peers.clear();
        }
    }
    private void closeConnectionInternal(PeerConnection peer) {
        peer.close();
        ServerMain.log.info("Removing " + peer.getForeignName() + " from peer list");
        server.getReadWriteManager().cancelPeerFiles(peer);

        // return the plain name to the queue, if it's not the default
        String plainName = peer.getName();
        if (!plainName.equals(DEFAULT_NAME)) {
            names.add(plainName);
        }
    }

    protected void setSocket(SocketWrapper value) {
        socket.set(value);
    }

    protected Optional<DatagramSocket> awaitUDPSocket() {
        try {
            SocketWrapper value = socket.await();
            if (value instanceof UDPSocket) {
                return Optional.of(((UDPSocket) value).get());
            }
            throw new RuntimeException("Expected UDPSocket, had " + value.getClass());
        } catch (InterruptedException e) {
            ServerMain.log.warning("Thread interrupted while waiting for socket: " + e.getMessage());
        }
        return Optional.empty();
    }

    protected Optional<ServerSocket> awaitTCPSocket() {
        try {
            SocketWrapper value = socket.await();
            if (value instanceof TCPSocket) {
                return Optional.of(((TCPSocket) value).get());
            }
            throw new RuntimeException("Expected TCPSocket, had " + value.getClass());
        } catch (InterruptedException e) {
            ServerMain.log.warning("Thread interrupted while waiting for socket: " + e.getMessage());
        }
        return Optional.empty();
    }

    protected boolean hasPeer(HostPort hostPort) {
        return getPeer(hostPort) != null;
    }

    public PeerConnection getPeer(HostPort hostPort) {
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

    public List<PeerConnection> getActivePeers() {
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

    protected String getAnyName() {
        return Optional.ofNullable(names.poll())
                .orElse(DEFAULT_NAME);
    }

    abstract void acceptConnections() throws IOException;
    abstract PeerConnection tryPeer(HostPort address);

    private void acceptConnectionsPersistent() {
        try {
            acceptConnections();
        } catch (Exception e) {
            ServerMain.log.severe("Accepting connections failed: " + e.getMessage());
        } finally {
            if (active.get()) {
                ServerMain.log.info("Restarting accept thread");
                if (socket.get() != null && socket.get().isClosed()) {
                    socket.reset();
                }
                acceptThread = new Thread(this::acceptConnectionsPersistent);
                acceptThread.start();
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
            e.printStackTrace();
            ServerMain.log.severe("Retrying peers failed: " + e.getMessage());
        } finally {
            if (active.get()) {
                ServerMain.log.info("Restarting peer retry thread");
                connectThread = new Thread(this::connectToPeers);
                connectThread.start();
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
