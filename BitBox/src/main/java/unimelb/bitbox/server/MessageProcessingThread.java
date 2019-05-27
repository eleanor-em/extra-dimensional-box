package unimelb.bitbox.server;

import org.jetbrains.annotations.NotNull;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.peers.FileReadWriteThreadPool;
import unimelb.bitbox.peers.PeerConnection;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.ResponseFormatException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
		JSONDocument document;
		// first check the message is correct JSON
		try {
			document = JSONDocument.parse(text);
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

    private void respondToMessage(PeerConnection peer, @NotNull String command, JSONDocument document)
            throws ResponseFormatException {
        Message parsedResponse = null;
        switch (command) {
            /*
             * File and directory requests
             */
            case Message.FILE_CREATE_REQUEST:
				validateFileDescriptor(document);

                String pathName = document.require("pathName");
                JSONDocument fileDescriptor = document.require("fileDescriptor");

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

                if (peer.needsRequest()) {
                    // we need to pass the host and port we received, as the socketContainer's data may not be accurate
                    // (since this socketContainer was an accepted connection)

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

                if (peer.needsResponse()) {
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
                if (!peer.needsResponse()) {
                    // why did they send this to us..?
                    invalidProtocolResponse(peer, "unexpected CONNECTION_REFUSED");
                }
                ServerMain.log.warning("Connection refused: " + document.<String>require("message"));
                peer.close();

                // now try to connect to the provided peer list
                ArrayList<JSONDocument> peers = document.requireArray("peers");
                for (JSONDocument peerHostPort : peers) {
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

    private void validateFileDescriptor(JSONDocument document) throws ResponseFormatException {
        JSONDocument fileDescriptor = document.require("fileDescriptor");
        fileDescriptor.<String>require("md5");
        fileDescriptor.<Long>require("lastModified");
        fileDescriptor.<Long>require("fileSize");
    }

    private void checkStatus(JSONDocument document) throws ResponseFormatException {
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
