package unimelb.bitbox;

import org.jetbrains.annotations.NotNull;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.util.*;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
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
        } catch (ResponseFormatException e){
            invalidProtocolResponse(message.peer, e.getMessage());
        }
	}

	/**
	 * Respond to the message, after error checking and parsing.
	 */
	// TODO: more general validation method? Andrea: Refactored some methods, see below
	private void respondToMessage(PeerConnection peer, String command, JsonDocument document)
            throws ResponseFormatException {
        switch (command) {
            /*
             * File and directory requests
             */
            case Message.FILE_CREATE_REQUEST:
                String pathName = document.require("pathName");
                JsonDocument fileDescriptor = document.require("fileDescriptor");
                String md5 = fileDescriptor.require("md5");
                if (!fileCreated(peer, md5, pathName)) {
                    ServerMain.log.info(peer.name + ": file " + pathName +
                            " not available locally. Send a FILE_BYTES_REQUEST");
                    // ELEANOR: Check that the response was successful before opening the file loader.
                    FileCreateResponse response = new FileCreateResponse(server.fileSystemManager, pathName, fileDescriptor);
                    peer.sendMessage(response);
                    if (response.successful && noLocalCopies(peer, pathName)) {
                        rwManager.addFile(peer, pathName, fileDescriptor);
                    }
                }
                break;
            case Message.FILE_MODIFY_REQUEST:
                pathName = document.require("pathName");
                fileDescriptor = document.require("fileDescriptor");
                FileModifyResponse response = new FileModifyResponse(server.fileSystemManager, fileDescriptor, pathName);
                peer.sendMessage(response);
                if (response.successful) {
                    rwManager.addFile(peer, pathName, fileDescriptor);
                }
                break;
            case Message.FILE_BYTES_REQUEST:
                rwManager.readFile(peer, document);
                break;
            case Message.FILE_DELETE_REQUEST:
                pathName = document.require("pathName");
                peer.sendMessage(new FileDeleteResponse(server.fileSystemManager, document, pathName));
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
            case Message.DIRECTORY_CREATE_RESPONSE:
            case Message.DIRECTORY_DELETE_RESPONSE:
                if (!document.<Boolean>require("status")) {
                    // ELEANOR: Log any unsuccessful responses.
                    ServerMain.log.warning("Failed response: " + command + ": " + document.require("message"));
                }
                break;

            case Message.FILE_BYTES_RESPONSE:
                rwManager.writeFile(peer, document);
                break;

            /*
             * Handshake request and responses
             */
            case Message.HANDSHAKE_REQUEST:
            	try {
					JsonDocument hostPort = document.require("hostPort");
					if (peer.getState() == PeerConnection.State.WAIT_FOR_REQUEST) {
						// we need to pass the host and port we received, as the socket's data may not be accurate
						// (since this socket was an accepted connection)
						String host = hostPort.require("host");
						int port = (int) (long) hostPort.<Long>require("port");
						ServerMain.log.info("Received connection request from " + host + ":" + port);

						// ELEANOR: this has to be done here because we don't know the foreign port until now
						// refuse connection if we are already connected to this address
						if (server.getConnectedAddresses().contains(host + ":" + port)) {
							peer.activate(host, port);
							peer.sendMessageAndClose(new ConnectionRefused(server.getPeers()));
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
					// a useful response. Then, re-throw the exception.
            		peer.activate();
            		throw e;
				}
                break;

            case Message.HANDSHAKE_RESPONSE:
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
                // now try to connect to the provided peer list
                // the Document interface sucks, so this code also sucks
                ArrayList<JsonDocument> peers = document.require("peers");
                for (JsonDocument doc : peers) {
                    String host = doc.require("host");
                    // the parser sucks, so the port becomes a long
                    long port = doc.require("port");

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
                ServerMain.log.severe("Invalid protocol response from "
                        + peer.name + ": " + document.require("message"));
                peer.close();
                break;

            default:
                invalidProtocolResponse(peer, "unrecognised command `" + command + "`");
                break;
        }
	}

	/**
	 * This method checks if a file was created with the same name and content.
	 */
	private boolean fileCreated(PeerConnection peer, String md5, String pathName){
		boolean fileExist = server.fileSystemManager.fileNameExists(pathName, md5);
		if (fileExist){
			ServerMain.log.info(peer.name + ": file " + pathName + " created already." +
					" No file create request is needed");
		}
		return fileExist;
	}

	/**
	 * This method checks if any local file has the same content. If any, copy the content and
	 * close the file loader.
	 */
	private boolean noLocalCopies(PeerConnection peer, String pathName){
		boolean notExist = false;
		try {
			notExist = server.fileSystemManager.checkShortcut(pathName);
		}
		catch (IOException | NoSuchAlgorithmException e){
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
	private final int serverPort;
	private final String advertisedName;
	// for debugging purposes, each of the threads is given a different name
	private final Queue<String> names = new ConcurrentLinkedQueue<>();

	private final Set<String> peerAddresses = ConcurrentHashMap.newKeySet();

	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		// initialise some stuff
		fileSystemManager = new FileSystemManager(
				Configuration.getConfigurationValue("path"),this);
		processor = new MessageProcessingThread(this);
		serverPort = Integer.parseInt(Configuration.getConfigurationValue("port"));
		advertisedName = Configuration.getConfigurationValue("advertisedName");
		createNames();

		// create the processor thread
		processor.start();
		log.info("Processor thread started");

		// create the server thread
		new Thread(this::acceptConnections).start();
		log.info("Server thread started");

		// connect to each of the listed peers
		String[] addresses = Configuration.getConfigurationValue("peers").split(",");
		peerAddresses.addAll(Arrays.asList(addresses));
		// start the peer connection thread
		new Thread(this::connectToPeers).start();
		log.info("Peer connection thread started");

		// create the synchroniser thread
		new Thread(this::regularlySynchronise).start();
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

	public List<PeerConnection> getPeers() {
		return peers.stream()
				.filter(peer -> peer.getState() == PeerConnection.State.ACTIVE)
				.collect(Collectors.toList());
	}

	private long getIncomingPeerCount() {
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
	private void acceptConnections() {
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
							log.info("Maximum number of peers reached.");
							out.write(new ConnectionRefused(getPeers()).encode());
							out.flush();
							log.info("Sending CONNECTION_REFUSED");
						} catch (IOException e) {
							log.warning("Failed writing CONNECTION_REFUSED");
						} finally {
							socket.close();
						}
					} else {
						String name = formatName(names.poll());
						peers.add(new PeerConnection(name,
								socket,
								this,
								PeerConnection.State.WAIT_FOR_REQUEST));
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

	/**
	 * This method needs to be called when a new set of peers are added.
	 */
	public void retryPeers() {
		for (Iterator<String> i = peerAddresses.iterator(); i.hasNext(); ) {
			String addr = i.next();
			if (getConnectedAddresses().contains(addr)) {
				continue;
			}
			// separate the address into a hostname and port
			// HostPort doesn't handle this safely
			String[] parts = addr.trim().split(":");
			if (parts.length > 1) {
				String hostname = parts[0];
				int port = Integer.parseInt(parts[1]);

				try {
					Socket socket = new Socket(hostname, port);

					// find a name
					String name = names.poll();
					if (name == null) {
						name = DEFAULT_NAME;
					}
					peers.add(new PeerConnection(formatName(name),
							socket,
							this,
							PeerConnection.State.WAIT_FOR_RESPONSE));
					// success: remove this peer from the set of peers to connect to
					i.remove();
					log.info("Connected to peer " + name + " (" + addr + ")");
				} catch (IOException e) {
					log.warning("Connection to peer `" + addr + "` failed: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * Broadcasts a message to all connected peers.
	 */
	private void broadcastMessage(Message message) {
		getPeers().forEach(peer -> peer.sendMessage(message));
	}


	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		switch (fileSystemEvent.event) {
			case DIRECTORY_CREATE:
				broadcastMessage(new DirectoryCreateRequest(fileSystemEvent.pathName));
				break;
			case DIRECTORY_DELETE:
				broadcastMessage(new DirectoryDeleteRequest(fileSystemEvent.pathName));
				break;
			case FILE_CREATE:
				broadcastMessage(new FileCreateRequest(fileSystemEvent.fileDescriptor, fileSystemEvent.pathName));
				break;
			case FILE_DELETE:
				broadcastMessage(new FileDeleteRequest(fileSystemEvent.fileDescriptor, fileSystemEvent.pathName));
				break;
			case FILE_MODIFY:
				broadcastMessage(new FileModifyRequest(fileSystemEvent.fileDescriptor, fileSystemEvent.pathName));
				break;
		}
	}

	@org.jetbrains.annotations.Contract(pure = true)
	private String formatName(String name) {
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
