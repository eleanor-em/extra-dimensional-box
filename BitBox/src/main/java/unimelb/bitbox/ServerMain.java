package unimelb.bitbox;

import org.jetbrains.annotations.NotNull;
import unimelb.bitbox.client.Server;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.util.config.CfgDependent;
import unimelb.bitbox.util.config.CfgEnumValue;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.config.Configuration;
import unimelb.bitbox.util.fs.FileSystemManager;
import unimelb.bitbox.util.fs.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.fs.FileSystemObserver;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.HostPortParseException;
import unimelb.bitbox.util.network.JsonDocument;
import unimelb.bitbox.util.network.ResponseFormatException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The ServerThread collects messages from the various PeerConnections, and then does something with them.
 */
class MessageProcessingThread extends Thread {
    private ServerMain server;
    public final FileReadWriteThreadPool rwManager;
    final BlockingQueue<ReceivedMessage> messages = new LinkedBlockingQueue<>();

    public MessageProcessingThread(ServerMain server) {
        this.server = server;
        this.rwManager = new FileReadWriteThreadPool(this.server);
    }

    @Override
    public void run() {
        try {
            while (true) {
                ReceivedMessage message = messages.take();
                processMessage(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ServerMain.log.severe("Restarting message processor");
            server.restartProcessingThread();
        }
    }

	/**
	 * Perform error checking, and send appropriate reply messages.
	 */
	private void processMessage(@NotNull ReceivedMessage message) {
		String text = message.text;
		JsonDocument document;
		// first check the message is correct JSON
		try {
			document = JsonDocument.parse(text);
		} catch (ResponseFormatException e) {
			ServerMain.log.warning(e.getMessage());
			invalidProtocolResponse(message.peer, "message must be valid JSON data");
			return;
		}

        // try to respond to the message
        String command;
        try {
            command = document.require("command");
            Optional<String> friendlyName = document.get("friendlyName");

            // if we got a friendly name, log it
            String logMessage = message.peer.getForeignName() + " received: " + command
                    + friendlyName.map(name -> " (via " + name + ")").orElse("");
            ServerMain.log.info(logMessage);
            respondToMessage(message.peer, command, document);
        } catch (ResponseFormatException e) {
            invalidProtocolResponse(message.peer, e.getMessage());
        }
    }

    /**
     * Respond to the message, after error checking and parsing.
     */

    private void respondToMessage(PeerConnection peer, @NotNull String command, JsonDocument document)
            throws ResponseFormatException {
        Message parsedResponse = null;
        switch (command) {
            /*
             * File and directory requests
             */
            case Message.FILE_CREATE_REQUEST:
				validateFileDescriptor(document);

                String pathName = document.require("pathName");
                JsonDocument fileDescriptor = document.require("fileDescriptor");

                FileCreateResponse createResponse = new FileCreateResponse(server.fileSystemManager, pathName, fileDescriptor, false);
                peer.sendMessage(createResponse);
                if (createResponse.successful && noLocalCopies(peer, pathName)) {
                    ServerMain.log.info(peer.getForeignName() + ": file " + pathName +
                            " not available locally. Send a FILE_BYTES_REQUEST");
                    rwManager.addFile(peer, pathName, fileDescriptor);
                }
                break;
            case Message.FILE_MODIFY_REQUEST:
                validateFileDescriptor(document);
                pathName = document.require("pathName");
                fileDescriptor = document.require("fileDescriptor");

                FileModifyResponse modifyResponse = new FileModifyResponse(server.fileSystemManager, fileDescriptor, pathName, false);
                peer.sendMessage(modifyResponse);
                if (modifyResponse.successful) {
                    rwManager.addFile(peer, pathName, fileDescriptor);
                }
                break;
            case Message.FILE_BYTES_REQUEST:
                validateFileDescriptor(document);
                document.<String>require("pathName");
                document.<Long>require("position");
                document.<Long>require("length");

                rwManager.readFile(peer, document);
                break;
            case Message.FILE_DELETE_REQUEST:
                validateFileDescriptor(document);
                pathName = document.require("pathName");
                fileDescriptor = document.require("fileDescriptor");

                peer.sendMessage(new FileDeleteResponse(server.fileSystemManager, fileDescriptor, pathName, false));
                break;

            case Message.DIRECTORY_CREATE_REQUEST:
                pathName = document.require("pathName");

                peer.sendMessage(new DirectoryCreateResponse(server.fileSystemManager, pathName, false));
                break;

            case Message.DIRECTORY_DELETE_REQUEST:
                pathName = document.require("pathName");

                peer.sendMessage(new DirectoryDeleteResponse(server.fileSystemManager, pathName, false));
                break;

            /*
             * File and directory responses
             */
            case Message.FILE_CREATE_RESPONSE:
                validateFileDescriptor(document);
                checkStatus(document);
                parsedResponse = new FileCreateResponse(server.fileSystemManager, document.require("pathName"), document.require("fileDescriptor"), true);
                break;
            case Message.FILE_DELETE_RESPONSE:
                validateFileDescriptor(document);
                checkStatus(document);
                parsedResponse = new FileDeleteResponse(server.fileSystemManager, document.require("fileDescriptor"), document.require("pathName"), true);
                break;
            case Message.FILE_MODIFY_RESPONSE:
                validateFileDescriptor(document);
                checkStatus(document);
                parsedResponse = new FileModifyResponse(server.fileSystemManager, document.require("fileDescriptor"), document.require("pathName"), true);
                break;
            case Message.DIRECTORY_CREATE_RESPONSE:
                checkStatus(document);
                parsedResponse = new DirectoryCreateResponse(server.fileSystemManager, document.require("pathName"), true);
                break;
            case Message.DIRECTORY_DELETE_RESPONSE:
                checkStatus(document);
                parsedResponse = new DirectoryDeleteResponse(server.fileSystemManager, document.require("pathName"), true);
                break;

            case Message.FILE_BYTES_RESPONSE:
                checkStatus(document);
                validateFileDescriptor(document);
                document.<String>require("pathName");
                document.<Long>require("length");
                document.<String>require("content");
                document.<String>require("message");
                document.<Boolean>require("status");
                parsedResponse = new FileBytesResponse(document.require("fileDescriptor"),
                                                       document.require("pathName"),
                                                       document.require("length"),
                                                       document.require("position"),
                                                       document.require("content"),
                                                  "", false);

                rwManager.writeFile(peer, document);
                break;

            /*
             * Handshake request and responses
             */
            case Message.HANDSHAKE_REQUEST:
                HostPort hostPort = HostPort.fromJSON(document.require("hostPort"));
                ServerMain.log.info("Received connection request from " + hostPort);

                if (peer.getState() == PeerConnection.State.WAIT_FOR_REQUEST) {
                    // we need to pass the host and port we received, as the socket's data may not be accurate
                    // (since this socket was an accepted connection)

                    // ELEANOR: this has to be done here because we don't know the foreign port until now
                    // refuse connection if we are already connected to this address
                    if (server.getOutgoingAddresses().contains(hostPort)) {
                        ServerMain.log.warning("Already connected to " + hostPort);
                        peer.close();
                    } else {
                        peer.activate(hostPort);
                        peer.sendMessage(new HandshakeResponse(false));
                        // synchronise with this peer
                        server.synchroniseFiles();
                    }
                } else {
                    // EXTENSION: Just ignore unexpected handshakes.
                    //invalidProtocolResponse(peer, "unexpected HANDSHAKE_REQUEST");
                    peer.activate(hostPort);
                    peer.sendMessage(new HandshakeResponse(false));
                    server.synchroniseFiles();
                }
                break;

            case Message.HANDSHAKE_RESPONSE:
                hostPort = HostPort.fromJSON(document.require("hostPort"));
                parsedResponse = new HandshakeResponse(true);

                if (peer.getState() == PeerConnection.State.WAIT_FOR_RESPONSE) {
                    peer.activate(hostPort);
                    // synchronise with this peer
                    server.synchroniseFiles();
                }
                // EXTENSION: Just ignore unexpected handshakes.
                /* else {
                    /invalidProtocolResponse(peer, "unexpected HANDSHAKE_RESPONSE");
                }*/
                break;

            case Message.CONNECTION_REFUSED:
                if (peer.getState() != PeerConnection.State.WAIT_FOR_RESPONSE) {
                    // why did they send this to us..?
                    invalidProtocolResponse(peer, "unexpected CONNECTION_REFUSED");
                }
                ServerMain.log.warning("Connection refused: " + document.<String>require("message"));
                peer.close();

                // now try to connect to the provided peer list
                ArrayList<JsonDocument> peers = document.requireArray("peers");
                for (JsonDocument peerHostPort : peers) {
                    String address = HostPort.fromJSON(peerHostPort).toString();
                    server.addPeerAddress(address);
                    ServerMain.log.info("Added peer `" + address + "`");
                    server.retryPeers();
                }
                break;

            /*
             * Invalid protocol messages
             */
            case Message.INVALID_PROTOCOL:
                // crap.
                ServerMain.log.severe("Invalid protocol response from "
                        + peer.getForeignName() + ": " + document.require("message"));
                peer.close();
                break;

            default:
                invalidProtocolResponse(peer, "unrecognised command `" + command + "`");
                break;
        }
        if (parsedResponse != null) {
            peer.notify(parsedResponse);
        }
    }

    private void validateFileDescriptor(JsonDocument document) throws ResponseFormatException {
        JsonDocument fileDescriptor = document.require("fileDescriptor");
        fileDescriptor.<String>require("md5");
        fileDescriptor.<Long>require("lastModified");
        fileDescriptor.<Long>require("fileSize");
    }

    private void checkStatus(JsonDocument document) throws ResponseFormatException {
        String message = document.require("message");
        boolean status = document.require("status");

        if (!status) {
            // ELEANOR: Log any unsuccessful responses.
            ServerMain.log.warning("Received failed " + document.require("command") + ": " + message);
        }
    }

    /**
     * This method checks if any local file has the same content. If any, copy the content and
     * close the file loader.
     */
    private boolean noLocalCopies(PeerConnection peer, String pathName) {
        boolean notExist = false;
        try {
            notExist = server.fileSystemManager.checkShortcut(pathName);
        } catch (IOException e) {
            ServerMain.log.severe(peer.getForeignName() + ": error checking shortcut for " + pathName);
        }
        return !notExist;
    }

    /**
     * A helper method to send an INVALID_PROTOCOL message.
     */
    private void invalidProtocolResponse(@NotNull PeerConnection peer, String message) {
        peer.activateDefault();
        peer.sendMessageAndClose(new InvalidProtocol(peer, message));
    }
}


public class ServerMain implements FileSystemObserver {
    public enum ConnectionMode {
        TCP,
        UDP
    }

    static final public Logger log = Logger.getLogger(ServerMain.class.getName());
    private static final int PEER_RETRY_TIME = 60;
    private static final String DEFAULT_NAME = "Anonymous";
    public static long getBlockSize() {
        return blockSize.get();
    }
    final FileSystemManager fileSystemManager;

    private static final CfgValue<Integer> maxIncomingConnections = CfgValue.createInt("maximumIncommingConnections");
    private static final CfgValue<String> advertisedName = CfgValue.create("advertisedName");
    private static final CfgValue<Integer> tcpPort = CfgValue.createInt("port");
    private static final CfgValue<Integer> udpPort = CfgValue.createInt("udpPort");
    static final CfgEnumValue<ConnectionMode> mode = new CfgEnumValue<>("mode", ConnectionMode.class);
    private static final CfgValue<String[]> peersToConnect = CfgValue.create("peers", val -> val.split(","));

    /**
     * Create a thread-safe list of the peer connections this program has active.
     */
    private final List<PeerConnection> peers = Collections.synchronizedList(new ArrayList<>());
    // this is the thread that collects messages and processes them
    private MessageProcessingThread processor;
    private static CfgDependent<HostPort> hostPort = new CfgDependent<>(Arrays.asList(advertisedName, tcpPort, udpPort),
                                                                        ServerMain::calculateHostPort);
    public static HostPort getHostPort() {
        return hostPort.get();
    }

    private static CfgDependent<Long> blockSize = new CfgDependent<>(mode, ServerMain::calculateBlockSize);

    public void restartProcessingThread() {
        processor = new MessageProcessingThread(this);
        processor.start();
    }

    private static long calculateBlockSize() {
        long blockSize;
        blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        if (mode.get() == ConnectionMode.UDP) {
            blockSize = Math.min(blockSize, 8192);
        }
        return blockSize;
    }
    private static HostPort calculateHostPort() {
        int serverPort;
        if (mode.get() == ConnectionMode.TCP) {
            serverPort = tcpPort.get();
        } else {
            serverPort = udpPort.get();
        }
        return new HostPort(advertisedName.get(), serverPort);
    }

    // for debugging purposes, each of the threads is given a different name
    private final Queue<String> names = new ConcurrentLinkedQueue<>();
    private final Set<HostPort> peerAddresses = ConcurrentHashMap.newKeySet();

    private DatagramSocket udpSocket;
    private ServerSocket tcpServerSocket;

    public ServerMain() throws NumberFormatException, IOException {
        // initialise things
        KnownPeerTracker.load();
        fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
        processor = new MessageProcessingThread(this);
        createNames();

        // load the mode
        // data read from the config file

		// create the processor thread
		processor.start();
		log.info("Processor thread started");

		// create the server thread
		Thread acceptThread = new Thread(this::acceptConnections);
        log.info("Accepting thread started");
		acceptThread.start();

        // start the peer connection thread
        peersToConnect.setOnChanged(this::addPeerAddressAll);
        addPeerAddressAll(peersToConnect.get());
        Thread connectThread = new Thread(this::connectToPeers);
        connectThread.start();
        log.info("Peer connection thread started");

        mode.setOnChanged(() -> {
            closeAllConnections();
            try {
                if (tcpServerSocket != null) {
                    tcpServerSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (udpSocket != null) {
                udpSocket.close();
            }
            connectThread.interrupt();
            acceptThread.interrupt();
        });

		// ELEANOR: terminology is confusing, so we introduce consistency
		// create the server thread for the client
		new Thread(new Server(this)).start();
		log.info("Server thread started");


		// create the synchroniser thread
		new Thread(this::regularlySynchronise).start();
	}

    /**
     * This method loops through the list of provided peers and attempts to connect to each one,
     * creating a PeerConnection object per peer for communication.
     * <br/><br/>
     * It loops forever so that more peers can be added later.
     */
    private void connectToPeers() {
        try {
            while (true) {
                retryPeers();
                Thread.sleep(PEER_RETRY_TIME * 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            log.severe("Restarting peer connection thread");
            new Thread(this::connectToPeers).start();
        }
    }

    private void acceptConnections() {
        try {
            if (mode.get() == ConnectionMode.TCP) {
                acceptConnectionsTCP();
            } else {
                acceptConnectionsUDP();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            log.severe("Restarting peer acceptor thread");
            new Thread(this::acceptConnections).start();
        }
    }

    /**
     * Close the connection to the given peer.
     */
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
        processor.rwManager.cancelPeerFiles(peer);

        // return the plain name to the queue, if it's not the default
        String plainName = peer.getName();
        if (!plainName.equals(DEFAULT_NAME)) {
            names.add(plainName);
        }
    }

    public boolean hasPeer(HostPort hostPort) {
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

    /**
     * Adds a message to the queue of messages to be processed.
     */
    public void enqueueMessage(ReceivedMessage message) {
        processor.messages.add(message);
    }

    /**
     * Add an address to the list of peers to connect to.
     */
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

    public Collection<PeerConnection> getActivePeers() {
        synchronized (peers) {
            return peers.stream()
                    .filter(peer -> peer.getState() == PeerConnection.State.ACTIVE)
                    .collect(Collectors.toList());
        }
    }

    public long getIncomingPeerCount() {
        synchronized (peers) {
            return peers.stream()
                    .filter(peer -> !peer.getOutgoing())
                    .count();
        }
    }

    public Collection<HostPort> getOutgoingAddresses() {
        synchronized (peers) {
            return peers.stream()
                    .filter(PeerConnection::getOutgoing)
                    .map(PeerConnection::getHostPort)
                    .collect(Collectors.toList());
        }
    }

    // This method creates a server thread that continually accepts new connections from other peers
    // and then creates a PeerConnection object to communicate with them.
    private void acceptConnectionsTCP() {
        try {
            tcpServerSocket = new ServerSocket(hostPort.get().port);
        } catch (IOException e) {
            log.severe("Opening server socket on port " + hostPort.get().port + " failed: " + e.getMessage());
        }
        while (!tcpServerSocket.isClosed()) {
            try {
                Socket socket = tcpServerSocket.accept();

                // check we have room for more peers
                // (only count incoming connections)
                if (getIncomingPeerCount() >= maxIncomingConnections.get()) {
                    // if not, write a CONNECTION_REFUSED message and close the connection
                    try (BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                        out.write(new ConnectionRefused(getActivePeers()).encode());
                        out.flush();
                        log.info("Sending CONNECTION_REFUSED");
                    } catch (IOException e) {
                        log.warning("Failed writing CONNECTION_REFUSED");
                    } finally {
                        socket.close();
                    }
                } else {
                    String name = getAnyName();

                    PeerConnection peer = new PeerTCP(name, socket, this, PeerConnection.State.WAIT_FOR_REQUEST);
                    peers.add(peer);
                    log.info("Connected to peer " + peer);
                }
            } catch (IOException e) {
                log.warning("Failed connecting to peer");
                e.printStackTrace();
            }
        }
    }

    /**
	 * This method needs to be called when a new set of peers are added.
	 */
	public void retryPeers() {
		// Remove all peers that successfully connect.
		peerAddresses.removeIf(addr -> tryPeer(addr) != null);
	}

    private PeerConnection tryPeerTCP(HostPort peerHostPort) {
        if (hasPeer(peerHostPort)) {
            return null;
        }

        try {
            Socket socket = new Socket(peerHostPort.hostname, peerHostPort.port);

            // find a name
            String name = getAnyName();
            PeerConnection peer = new PeerTCP(name, socket, this, PeerConnection.State.WAIT_FOR_RESPONSE);
            peers.add(peer);
            // success: remove this peer from the set of peers to connect to
            log.info("Connected to peer " + name + " @ " + peerHostPort);
            return peer;
        } catch (IOException e) {
            log.warning("Connection to peer `" + peerHostPort + "` failed: " + e.getMessage());
            return null;
        }
    }

    public PeerConnection tryPeer(HostPort target) {
        // if it's already in our set, this does nothing, so just make sure (in case the peer is temporarily
        // unavailable)
        addPeerAddress(target);
        if (mode.get() == ConnectionMode.TCP) {
            return tryPeerTCP(target);
        } else {
            return tryPeerUDP(target);
        }
    }

    public boolean clientTryPeer(HostPort hostPort){
		if (getIncomingPeerCount() >= maxIncomingConnections.get()) {
			return false;
		}

		PeerConnection peer = tryPeer(hostPort);
		if (peer != null) {
			peer.forceIncoming();
			return true;
		}
		return false;
	}

    private void acceptConnectionsUDP() {
        // Maximum packet size is 65507 bytes
        byte[] buffer = new byte[65507];

        try (DatagramSocket udpSocket = new DatagramSocket(hostPort.get().port)) {
            udpSocket.setSoTimeout(1);
            this.udpSocket = udpSocket;
            while (!udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    HostPort hostPort = new HostPort(packet.getAddress().toString(), packet.getPort());

                    String name = getAnyName();
                    PeerConnection connectedPeer = getPeer(hostPort);

                    // Check if this is a new peer
                    if (connectedPeer == null) {
                        if (getIncomingPeerCount() < maxIncomingConnections.get()) {
                            // Create the peer if we have room for another
                            connectedPeer = new PeerUDP(name, this,
                                                        PeerConnection.State.WAIT_FOR_REQUEST,
                                                        udpSocket, packet);
                            peers.add(connectedPeer);
                        } else {
                            // Send CONNECTION_REFUSED
                            Message message = new ConnectionRefused(getActivePeers());
                            byte[] responseBuffer = (message.encode() + "\n").getBytes(StandardCharsets.UTF_8);
                            packet.setData(responseBuffer);
                            packet.setLength(responseBuffer.length);
                            udpSocket.send(packet);
                            continue;
                        }
                    }
                    // The actual message may be shorter than what we got from the socket
                    String packetData = new String(packet.getData(), 0, packet.getLength());
                    connectedPeer.receiveMessage(packetData);
                } catch (SocketTimeoutException ignored) {
                } catch (IOException e) {
                    log.severe("Failed receiving from peer: " + e.getMessage());
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
            log.severe("Error from UDP socket");
        }
    }

    private PeerConnection tryPeerUDP(HostPort peerHostPort) {
        if (hasPeer(peerHostPort)) {
            return null;
        }

        // check if the socket is available yet
        if (udpSocket == null) {
            // try again later
            return null;
        }

        String name = getAnyName();

        byte[] buffer = new byte[65507];
        //send handshake request
        DatagramPacket packet = new DatagramPacket(buffer,
                                                   buffer.length,
                                                   new InetSocketAddress(peerHostPort.hostname, peerHostPort.port));
        PeerUDP p = new PeerUDP(name, this, PeerConnection.State.WAIT_FOR_RESPONSE, udpSocket, packet);
        peers.add(p);
        log.info("Attempting to send handshake to " + name + " @ " + peerHostPort + ", waiting for response;");
        return p;
    }

    /**
     * Broadcasts a message to all connected peers.
     */
    private void broadcastMessage(Message message) {
        getActivePeers().forEach(peer -> peer.sendMessage(message));
    }

    private JsonDocument docFileDescriptor(FileSystemManager.FileDescriptor fd) {
        if (fd == null) {
            return null;
        }

        JsonDocument doc = new JsonDocument();
        doc.append("md5", fd.md5);
        doc.append("lastModified", fd.lastModified);
        doc.append("fileSize", fd.fileSize);
        return doc;
    }

    @Override
    public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
        JsonDocument fileDescriptor = docFileDescriptor(fileSystemEvent.fileDescriptor);
        switch (fileSystemEvent.event) {
            case DIRECTORY_CREATE:
                broadcastMessage(new DirectoryCreateRequest(fileSystemEvent.pathName));
                break;
            case DIRECTORY_DELETE:
                broadcastMessage(new DirectoryDeleteRequest(fileSystemEvent.pathName));
                break;
            case FILE_CREATE:
                broadcastMessage(new FileCreateRequest(fileDescriptor, fileSystemEvent.pathName));
                break;
            case FILE_DELETE:
                broadcastMessage(new FileDeleteRequest(fileDescriptor, fileSystemEvent.pathName));
                break;
            case FILE_MODIFY:
                broadcastMessage(new FileModifyRequest(fileDescriptor, fileSystemEvent.pathName));
                break;
        }
    }

    public String getAnyName() {
        return Optional.ofNullable(names.poll())
                       .orElse(DEFAULT_NAME);
    }

    /**
     * Generate the synchronisation events, and send them to peers.
     */
    public void synchroniseFiles() {
        fileSystemManager.generateSyncEvents()
                .forEach(this::processFileSystemEvent);
    }

    private void regularlySynchronise() {
        final int SYNC_INTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
        try {
            while (true) {
                try {
                    Thread.sleep(SYNC_INTERVAL * 1000);
                } catch (InterruptedException e) {
                    log.warning("Synchronise thread interrupted");
                }
                synchroniseFiles();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            log.severe("Restarting synchroniser thread");
            new Thread(this::regularlySynchronise).start();
        }
    }
}
