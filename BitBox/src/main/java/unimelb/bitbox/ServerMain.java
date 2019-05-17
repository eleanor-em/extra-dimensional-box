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
                if (messages.size() == 0)
                    continue;
                ReceivedMessage message = messages.take();
                processMessage(message);
            }
        } catch (InterruptedException e) {
            ServerMain.log.severe("Message processor interrupted: " + e.getMessage());
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
            e.printStackTrace();
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
            String logMessage = message.peer.name + " received: " + command
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
        switch (command) {
            /*
             * File and directory requests
             */
            case Message.FILE_CREATE_REQUEST:
                validateFileDescriptor(document);

                String pathName = document.require("pathName");
                JsonDocument fileDescriptor = document.require("fileDescriptor");

				FileCreateResponse createResponse = new FileCreateResponse(server.fileSystemManager, pathName, fileDescriptor);
				peer.sendMessage(createResponse);
				if (createResponse.successful && noLocalCopies(peer, pathName)) {
				ServerMain.log.info(peer.name + ": file " + pathName +
						" not available locally. Send a FILE_BYTES_REQUEST");
				// ELEANOR: Check that the createResponse was successful before opening the file loader.

					rwManager.addFile(peer, pathName, fileDescriptor);
				}
                break;
            case Message.FILE_MODIFY_REQUEST:
                validateFileDescriptor(document);
                pathName = document.require("pathName");
                fileDescriptor = document.require("fileDescriptor");

                FileModifyResponse modifyResponse = new FileModifyResponse(server.fileSystemManager, fileDescriptor, pathName);
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

                peer.sendMessage(new FileDeleteResponse(server.fileSystemManager, fileDescriptor, pathName));
                break;

            case Message.DIRECTORY_CREATE_REQUEST:
                pathName = document.require("pathName");

                peer.sendMessage(new DirectoryCreateResponse(server.fileSystemManager, pathName));
                break;

            case Message.DIRECTORY_DELETE_REQUEST:
                pathName = document.require("pathName");

                peer.sendMessage(new DirectoryDeleteResponse(server.fileSystemManager, pathName));
                break;

            /*
             * File and directory responses
             */
            case Message.FILE_CREATE_RESPONSE:
            case Message.FILE_DELETE_RESPONSE:
            case Message.FILE_MODIFY_RESPONSE:
                validateFileDescriptor(document);
            case Message.DIRECTORY_CREATE_RESPONSE:
            case Message.DIRECTORY_DELETE_RESPONSE:
                document.<String>require("pathName");
                String message = document.require("message");
                boolean status = document.<Boolean>require("status");

                if (!status) {
                    // ELEANOR: Log any unsuccessful responses.
                    ServerMain.log.warning("Failed createResponse: " + command + ": " + message);
                }
                break;

            case Message.FILE_BYTES_RESPONSE:
                validateFileDescriptor(document);
                document.<String>require("pathName");
                document.<Long>require("length");
                document.<String>require("content");
                document.<String>require("message");
                document.<Boolean>require("status");

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
                        if (server.getConnectedAddresses().contains(host + ":" + port)) {
                            peer.activate(host, port);
                            peer.sendMessageAndClose(new ConnectionRefused(server.getActivePeers()));
                            ServerMain.log.info("Already connected to " + host + ":" + port);
                        } else {
                            peer.activate(host, port);
                            peer.sendMessage(new HandshakeResponse(peer.getLocalHost(), peer.getLocalPort()));
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
                hostPort.<String>require("host");
                hostPort.<Long>require("port");

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
                ServerMain.log.info("Connection refused: " + document.<String>require("message"));

                // now try to connect to the provided peer list
                ArrayList<JsonDocument> peers = document.require("peers");
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
                        + peer.name + ": " + document.require("message"));
                peer.close();
                break;

            default:
                invalidProtocolResponse(peer, "unrecognised command `" + command + "`");
                break;
        }
    }

    private void validateFileDescriptor(JsonDocument document) throws ResponseFormatException {
        JsonDocument fileDescriptor = document.require("fileDescriptor");
        fileDescriptor.<String>require("md5");
        fileDescriptor.<Long>require("lastModified");
        fileDescriptor.<Long>require("fileSize");
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
            ServerMain.log.severe(peer.name + ": error checking shortcut for " + pathName);
        }
        return !notExist;
    }

    /**
     * A helper method to send an INVALID_PROTOCOL message.
     */
    private void invalidProtocolResponse(@NotNull PeerConnection peer, String message) {
        ServerMain.log.info("Closing connection to " + peer.name + ": " + message);
        peer.sendMessageAndClose(new InvalidProtocol(message));
    }
}

public class ServerMain implements FileSystemObserver {
    static final public Logger log = Logger.getLogger(ServerMain.class.getName());
    private static final int PEER_RETRY_TIME = 60;
    private static final String DEFAULT_NAME = "Anonymous";
    final FileSystemManager fileSystemManager;
    /**
     * Create a thread-safe list of the peer connections this program has active.
     */
    private final List<PeerConnection> peers = Collections.synchronizedList(new ArrayList<>());
    // this is the thread that collects messages and processes them
    private final MessageProcessingThread processor;
    // data read from the config file
    private int serverPort;
    private final String advertisedName;

    private enum CONNECTION_MODE {
        TCP,
        UDP
    }
    private final CONNECTION_MODE mode;
    // for debugging purposes, each of the threads is given a different name
    private final Queue<String> names = new ConcurrentLinkedQueue<>();
    private final Set<String> peerAddresses = ConcurrentHashMap.newKeySet();

    private DatagramSocket udpSocket;

    public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
        // initialise some stuff
        fileSystemManager = new FileSystemManager(
                Configuration.getConfigurationValue("path"), this);
        advertisedName = Configuration.getConfigurationValue("advertisedName");
        createNames();
        // load the mode
        switch (Configuration.getConfigurationValue("mode")) {
            case "tcp":
                mode = CONNECTION_MODE.TCP;
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

        processor = new MessageProcessingThread(this);

        // create the processor thread
        processor.start();
        log.info("Processor thread started");

        // ELEANOR: terminology is confusing, so we introduce consistency
        // create the server thread for the client
        new Thread(new Server(this)).start();
        log.info("Server thread started");

        serverPort = Integer.parseInt(Configuration.getConfigurationValue("port"));

        // create the connection acceptor thread
        new Thread(this::acceptConnections).start();
        log.info("Connection acceptor thread started");

        // start the peer connection thread
        // connect to each of the listed peers
        String[] addresses = Configuration.getConfigurationValue("peers").split(",");
        peerAddresses.addAll(Arrays.asList(addresses));
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
        while (true) {
            try {
                retryPeers();
                Thread.sleep(PEER_RETRY_TIME * 1000);
            } catch (InterruptedException e) {
                log.warning("Peer connecting thread interrupted");
            }
        }
    }

    private void acceptConnections() {
        if (mode == CONNECTION_MODE.TCP) {
            acceptConnectionsTCP();
        } else {
            acceptConnectionsUDP();
        }
    }

    /**
     * Close the connection to the given peer.
     */
    public void closeConnection(PeerConnection peer) {
        peer.close();
        peers.remove(peer);
        processor.rwManager.cancelPeerFiles(peer);

        // return the plain name to the queue, if it's not the default
        String plainName = peer.getPlainName();
        if (!plainName.equals(DEFAULT_NAME)) {
            names.add(plainName);
        }
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
        peerAddresses.add(address);
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
    }

    public List<PeerConnection> getActivePeers() {
        return peers.stream()
                .filter(peer -> peer.getState() == PeerConnection.State.ACTIVE)
                .collect(Collectors.toList());
    }

    protected long getIncomingPeerCount() {
        return peers.stream()
                .filter(peer -> !peer.getOutgoing())
                .filter(peer -> peer.getState() == PeerConnection.State.ACTIVE)
                .count();
    }

    public List<String> getConnectedAddresses() {
        return peers.stream()
                .map(peer -> peer.getHost() + ":" + peer.getPort())
                .collect(Collectors.toList());
    }

    // This method creates a server thread that continually accepts new connections from other peers
    // and then creates a PeerConnection object to communicate with them.
    private void acceptConnectionsTCP() {
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            while (true) {
                try {
                    Socket socket = serverSocket.accept();

                    // check we have room for more peers
                    // (only count incoming connections)
                    if (getIncomingPeerCount() >= Integer.parseInt(
                            Configuration.getConfigurationValue("maximumIncommingConnections"))) {
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
        peerAddresses.removeIf(this::tryPeer);
    }

    private boolean tryPeerTCP(String peerAddress) {
        // if it's already in our set, this does nothing, so just make sure (in case the peer is temporarily
        // unavailable)
        addPeerAddress(peerAddress);

        if (getConnectedAddresses().contains(peerAddress)) {
            return false;
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
                peers.add(new PeerTCP(formatName(name),
                        socket,
                        this,
                        PeerConnection.State.WAIT_FOR_RESPONSE));
                // success: remove this peer from the set of peers to connect to
                log.info("Connected to peer " + name + " (" + peerAddress + ")");
                return true;
            } catch (IOException e) {
                log.warning("Connection to peer `" + peerAddress + "` failed: " + e.getMessage());
            }
        }
        return false;
    }

    public boolean tryPeer(String addr) {
        if (mode == CONNECTION_MODE.TCP) {
            return tryPeerTCP(addr);
        } else {
            return tryPeerUDP(addr);
        }
    }

    public boolean tryPeer(String hostname, int port) {
        String addr = hostname + ":" + port;
        return tryPeer(addr);
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
        // ELEANOR: why 25000..?
        byte[] buffer = new byte[25000];//extra 500 to accommodate headers
        try (DatagramSocket udpSocket = new DatagramSocket(serverPort)) {
            this.udpSocket = udpSocket;
            while (!udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    String hostPort = (packet.getAddress() + ":" + packet.getPort()).replace("/", "");

                    String name = formatName(names.poll());
                    PeerConnection connectedPeer = peers.stream()
                            .filter(peer -> hostPort.equals(peer.getHost() + ":" + peer.getPort()))
                            .findFirst()
                            .orElseGet(() -> {
                                final PeerUDP result = new PeerUDP(name, this,
                                        PeerConnection.State.WAIT_FOR_REQUEST,
                                        udpSocket, packet);
                                peers.add(result);
                                return result;
                            });

                    // Remove null bytes. Apparently String doesn't null terminate...
                    String packetData = new String(packet.getData());
                    packetData = packetData.substring(0, packetData.indexOf('\0'));
                    processor.messages.add(new ReceivedMessage(packetData, connectedPeer));
                } catch (IOException e) {
                    log.severe("Failed receiving from peer: " + e.getMessage());
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }


    }

    private boolean tryPeerUDP(String addr) {
        if (getConnectedAddresses().contains(addr)) {
            return false;
        }

        // check if the socket is available yet
        if (udpSocket == null) {
            // try again later
            return false;
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

            byte[] buffer = new byte[25000];
            //send handshake request,
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, new InetSocketAddress(hostname, port));
            //it should send handshake request without creating the peer.
            if (!getConnectedAddresses().contains(packet.getAddress().toString() + ":" + packet.getPort())) {
                PeerUDP p = new PeerUDP(formatName(name), this, PeerConnection.State.WAIT_FOR_RESPONSE, udpSocket, packet);
                peers.add(p);
                log.info("Attempting to send handshake to " + name + " (" + addr + "), waiting for response;");
                return true;
            }
        }
        return false;
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
        while (true) {
            try {
                Thread.sleep(SYNC_INTERVAL * 1000);
            } catch (InterruptedException e) {
                log.warning("Synchronise thread interrupted");
            }
            synchroniseFiles();
        }
    }
}

