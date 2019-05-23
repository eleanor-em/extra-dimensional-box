package unimelb.bitbox;

import org.jetbrains.annotations.NotNull;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.client.Server;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.util.*;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
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
		} catch (ParseException e) {
			ServerMain.log.warning("Error parsing `" + message + "`.");
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
        } catch (ResponseFormatException e){
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
                try {
                    JsonDocument hostPort = document.require("hostPort");
                    String host = hostPort.require("host");
                    long port = hostPort.require("port");

                    if (peer.getState() == PeerConnection.State.WAIT_FOR_REQUEST) {
                        // we need to pass the host and port we received, as the socket's data may not be accurate
                        // (since this socket was an accepted connection)
                        ServerMain.log.info("Received connection request from " + host + ":" + port);

                        // ELEANOR: this has to be done here because we don't know the foreign port until now
                        // refuse connection if we are already connected to this address
                        if (server.getOutgoingAddresses().contains(host + ":" + port)) {
                            peer.close();
                            ServerMain.log.warning("Already connected to " + host + ":" + port);
                        } else {
                            peer.activate(host, port);
                            peer.sendMessage(new HandshakeResponse(peer.getLocalHost(), peer.getLocalPort(), false));
                            // synchronise with this peer
                            server.synchroniseFiles();
                        }
                    } else {
                        invalidProtocolResponse(peer, "unexpected HANDSHAKE_REQUEST");
                    }
                } catch (ResponseFormatException e) {
                    // In case there was an issue with the format, the peer needs to be activated so it can provide
                    // a useful createResponse. Then, re-throw the exception.
                    peer.activate();
                    throw e;
                }
                break;

            case Message.HANDSHAKE_RESPONSE:
                JsonDocument hostPort = document.require("hostPort");
                parsedResponse = new HandshakeResponse(hostPort.require("host"), hostPort.require("port"), true);

                if (peer.getState() == PeerConnection.State.WAIT_FOR_RESPONSE) {
                    peer.activate();
                    // synchronise with this peer
                    server.synchroniseFiles();
                } else {
                    invalidProtocolResponse(peer, "unexpected HANDSHAKE_RESPONSE");
                }
                break;

            case Message.CONNECTION_REFUSED:
                if (peer.getState() != PeerConnection.State.WAIT_FOR_RESPONSE) {
                    // why did they send this to us..?
                    invalidProtocolResponse(peer, "unexpected CONNECTION_REFUSED");
                }
                peer.close();
                ServerMain.log.warning("Connection refused: " + document.<String>require("message"));

                // now try to connect to the provided peer list
                ArrayList<JsonDocument> peers = document.requireArray("peers");
                for (JsonDocument peerHostPort : peers) {
                    String host = peerHostPort.require("host");
                    long port = peerHostPort.require("port");

                    String address = host + ":" + port;

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
                ServerMain.log.severe("Invalid protocol createResponse from "
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
            ServerMain.log.info("Failed createResponse: " + document.require("command") + ": " + message);
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
        } catch (IOException | NoSuchAlgorithmException e) {
            ServerMain.log.severe(peer.getForeignName() + ": error checking shortcut for " + pathName);
        }
        return !notExist;
    }

    /**
     * A helper method to send an INVALID_PROTOCOL message.
     */
    private void invalidProtocolResponse(@NotNull PeerConnection peer, String message) {
        ServerMain.log.info("Closing connection to " + peer.getForeignName() + ": " + message);
        peer.activate();
        peer.sendMessageAndClose(new InvalidProtocol(message));
    }
}

public class ServerMain implements FileSystemObserver {
    static final public Logger log = Logger.getLogger(ServerMain.class.getName());
    private static final int PEER_RETRY_TIME = 60;
    private static final String DEFAULT_NAME = "Anonymous";
    private static long blockSize;
    public static long getBlockSize() {
        return blockSize;
    }
    final FileSystemManager fileSystemManager;

    private final int maxIncomingConnections;
    /**
     * Create a thread-safe list of the peer connections this program has active.
     */
    private final List<PeerConnection> peers = Collections.synchronizedList(new ArrayList<>());
    // this is the thread that collects messages and processes them
    private MessageProcessingThread processor;
    // data read from the config file
    private int serverPort;
    private final String advertisedName;

    public void restartProcessingThread() {
        processor = new MessageProcessingThread(this);
        processor.run();
    }

    public enum CONNECTION_MODE {
        TCP,
        UDP
    }

    public final CONNECTION_MODE mode;
    // for debugging purposes, each of the threads is given a different name
    private final Queue<String> names = new ConcurrentLinkedQueue<>();
    private final Set<String> peerAddresses = ConcurrentHashMap.newKeySet();

    private DatagramSocket udpSocket;

    public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
        // initialise some stuff
        KnownPeerTracker.load();
        fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
        processor = new MessageProcessingThread(this);
        advertisedName = Configuration.getConfigurationValue("advertisedName");
        maxIncomingConnections = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
        createNames();
        // load the mode
        switch (Configuration.getConfigurationValue("mode")) {
            case "tcp":
                mode = CONNECTION_MODE.TCP;
                serverPort = Integer.parseInt(Configuration.getConfigurationValue("port"));
                break;
            case "udp":
                mode = CONNECTION_MODE.UDP;
                serverPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
                break;
            default:
                mode = null;
                log.severe("Invalid mode set, process will be terminated.");
                System.exit(1);
        }
        if (mode == CONNECTION_MODE.TCP) {
            blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        } else {
            blockSize = Math.min(Long.parseLong(Configuration.getConfigurationValue("blockSize")), 8192);
        }

		// create the processor thread
		processor.start();
		log.info("Processor thread started");

		// create the server thread
		new Thread(this::acceptConnections).start();
		log.info("Peer-to-Peer server thread started");

		// ELEANOR: terminology is confusing, so we introduce consistency
		// create the server thread for the client
		new Thread(new Server(this)).start();
		log.info("Server thread started");

		// connect to each of the listed peers
		String[] addresses = Configuration.getConfigurationValue("peers").split(",");
		addPeerAddressAll(addresses);
		// start the peer connection thread
		new Thread(this::connectToPeers).start();
		log.info("Peer connection thread started");

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
                try {
                    retryPeers();
                    Thread.sleep(PEER_RETRY_TIME * 1000);
                } catch (InterruptedException e) {
                    log.warning("Peer connecting thread interrupted");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.severe("Restarting peer connection thread");
            new Thread(this::connectToPeers).start();
        }
    }

    private void acceptConnections() {
        try {
            if (mode == CONNECTION_MODE.TCP) {
                acceptConnectionsTCP();
            } else {
                acceptConnectionsUDP();
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.severe("Restarting peer acceptor thread");
            new Thread(this::acceptConnections).start();
        }
    }

    /**
     * Close the connection to the given peer.
     */
    public void closeConnection(PeerConnection peer) {
        peer.close();
        ServerMain.log.info("Removing " + peer.getForeignName() + " from peer list");
        peers.remove(peer);
        processor.rwManager.cancelPeerFiles(peer);

        // return the plain name to the queue, if it's not the default
        String plainName = peer.getPlainName();
        if (!plainName.equals(DEFAULT_NAME)) {
            names.add(plainName);
        }
    }

    public PeerConnection getPeer(String host, int port) {
        for (PeerConnection peer : getActivePeers()){
            if (peer.getHost().equalsIgnoreCase(host) && peer.getPort() == port){
                return peer;
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
        if (HostPort.validate(address)) {
            ServerMain.log.info("Adding address " + address + " to connection list.");
            peerAddresses.add(address);
        } else {
            ServerMain.log.warning("Tried to add invalid address `" + address + "`.");
        }
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

    public List<PeerConnection> getActivePeers() {
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
                    .filter(peer -> peer.getState() == PeerConnection.State.ACTIVE)
                    .count();
        }
    }

    public List<String> getCurrentAddresses() {
        synchronized (peers) {
            return peers.stream()
                    .map(peer -> peer.getHost() + ":" + peer.getPort())
                    .collect(Collectors.toList());
        }
    }
    public List<String> getOutgoingAddresses() {
        synchronized (peers) {
            return peers.stream()
                    .filter(PeerConnection::getOutgoing)
                    .map(peer -> peer.getHost() + ":" + peer.getPort())
                    .collect(Collectors.toList());
        }
    }

    // This method creates a server thread that continually accepts new connections from other peers
    // and then creates a PeerConnection object to communicate with them.
    private void acceptConnectionsTCP() {
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();

                    // check we have room for more peers
                    // (only count incoming connections)
                    if (getIncomingPeerCount() >= maxIncomingConnections) {
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
                        String name = formatName(names.poll());

                        peers.add(new PeerTCP(name, socket, this, PeerConnection.State.WAIT_FOR_REQUEST));
                        log.info("Connected to peer " + name);
                    }
                } catch (IOException e) {
                    log.warning("Failed connecting to peer.");
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            log.severe("Opening server socket on port " + serverPort + " failed: " + e.getMessage());
        }
    }

    /**
	 * This method needs to be called when a new set of peers are added.
	 */
	public void retryPeers() {
		// Remove all peers that successfully connect.
		peerAddresses.removeIf(addr -> tryPeer(addr) != null);
	}

    private PeerConnection tryPeerTCP(String peerAddress) {
        // if it's already in our set, this does nothing, so just make sure (in case the peer is temporarily
        // unavailable)
        addPeerAddress(peerAddress);

        if (getCurrentAddresses().contains(peerAddress)) {
            return null;
        }
        // separate the address into a hostname and port
        // HostPort doesn't handle this safely
        String[] parts = peerAddress.trim().split(":");
        if (parts.length > 1) {
            String hostname = parts[0];
            int port = Integer.parseInt(parts[1]);

            try {
                Socket socket = new Socket(hostname, port);

                // find a name
                String name = getName();
                PeerConnection peer = new PeerTCP(formatName(name),
                        socket,
                        this,
                        PeerConnection.State.WAIT_FOR_RESPONSE);
                peers.add(peer);
                // success: remove this peer from the set of peers to connect to
                log.info("Connected to peer " + name + " (" + peerAddress + ")");
            } catch (IOException e) {
                log.warning("Connection to peer `" + peerAddress + "` failed: " + e.getMessage());
            }
        }
        return null;
    }

    public PeerConnection tryPeer(String addr) {
        if (mode == CONNECTION_MODE.TCP) {
            return tryPeerTCP(addr);
        } else {
            return tryPeerUDP(addr);
        }
    }

    public boolean clientTryPeer(String hostname, int port){
		if (getIncomingPeerCount() >= maxIncomingConnections) {
			return false;
		}

		String addr = hostname + ":" + port;
		PeerConnection peer = tryPeer(addr);
		if (peer != null) {
			peer.forceIncoming();
			return true;
		}
		return false;
	}

    /**
     * Get a name for the peer connection for debugging purposes.
     *
     * @return A name for the peer connection
     */
    public String getName() {
        String name = names.poll();
        if (name == null) {
            name = DEFAULT_NAME;
        }
        return name;
    }

    private void acceptConnectionsUDP() {
        // Maximum packet size is 65507 bytes
        byte[] buffer = new byte[65507];

        try (DatagramSocket udpSocket = new DatagramSocket(serverPort)) {
            this.udpSocket = udpSocket;
            while (!udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    String hostPort = (packet.getAddress() + ":" + packet.getPort()).replace("/", "");

                    String name = formatName(names.poll());
                    PeerConnection connectedPeer;
                    synchronized (peers) {
                        connectedPeer = peers.stream()
                                .filter(peer -> hostPort.equals(peer.getHost() + ":" + peer.getPort()))
                                .findFirst()
                                .orElseGet(() -> {
                                    if (getIncomingPeerCount() < maxIncomingConnections) {
                                        final PeerUDP result = new PeerUDP(name, this,
                                                PeerConnection.State.WAIT_FOR_REQUEST,
                                                udpSocket, packet);
                                        peers.add(result);
                                        return result;
                                    } else {
                                        return null;
                                    }
                                });
                    }
                    // Send CONNECTION_REFUSED
                    if (connectedPeer == null) {
                        Message message = new ConnectionRefused(getActivePeers());
                        byte[] responseBuffer = message.encode().getBytes(StandardCharsets.UTF_8);
                        packet.setData(responseBuffer);
                        packet.setLength(responseBuffer.length);
                        udpSocket.send(packet);
                    } else {
                        // The actual message may be shorter than what we got from the socket
                        String packetData = new String(packet.getData(), 0, packet.getLength());
                        connectedPeer.receiveMessage(packetData);
                    }
                } catch (IOException e) {
                    log.severe("Failed receiving from peer: " + e.getMessage());
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
            log.severe("Error from UDP socket");
        }


    }

    private PeerConnection tryPeerUDP(String addr) {
        if (getCurrentAddresses().contains(addr)) {
            return null;
        }

        // check if the socket is available yet
        if (udpSocket == null) {
            // try again later
            return null;
        }

        // separate the address into a hostname and port
        // HostPort doesn't handle this safely
        String[] parts = addr.trim().split(":");
        if (parts.length > 1) {
            String hostname = parts[0];
            int port = Integer.parseInt(parts[1]);

            // find a name
            String name = names.poll();
            if (name == null) {
                name = DEFAULT_NAME;
            }

            byte[] buffer = new byte[65507];
            //send handshake request,
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, new InetSocketAddress(hostname, port));
            //it should send handshake request without creating the peer.
            if (!getCurrentAddresses().contains(packet.getAddress().toString() + ":" + packet.getPort())) {
                PeerUDP p = new PeerUDP(formatName(name), this, PeerConnection.State.WAIT_FOR_RESPONSE, udpSocket, packet);
                peers.add(p);
                log.info("Attempting to send handshake to " + name + " (" + addr + "), waiting for response;");
                return p;
            }
        }
        return null;
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

    @org.jetbrains.annotations.Contract(pure = true)
    public String formatName(String name) {
        if (name == null) {
            name = "Anonymous";
        }
        return name + "-" + advertisedName + ":" + serverPort;
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
            log.severe("Restarting synchroniser thread");
            new Thread(this::regularlySynchronise).start();
        }
    }
}