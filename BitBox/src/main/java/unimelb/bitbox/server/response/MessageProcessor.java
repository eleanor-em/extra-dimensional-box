package unimelb.bitbox.server.response;

import org.jetbrains.annotations.NotNull;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.peers.FileReadWriteThreadPool;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.functional.algebraic.Maybe;
import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The ServerThread collects messages from the various PeerConnections, and then does something with them.
 */
public class MessageProcessor implements Runnable  {
    private final PeerServer server;
    private final FileReadWriteThreadPool rwManager;
    private final BlockingQueue<ReceivedMessage> messages = new LinkedBlockingQueue<>();

    public MessageProcessor(PeerServer server) {
        this.server = server;
        this.rwManager = new FileReadWriteThreadPool(server);
    }

    public void add(ReceivedMessage message) {
        messages.add(message);
    }

    @Override
    public void run() {
        while (true) {
            try {
                processMessage(messages.take());
            } catch (InterruptedException e) {
                PeerServer.log.warning("receiving thread interrupted");
                e.printStackTrace();
            }
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
			document = JSONDocument.parse(text).get();
		} catch (JSONException e) {
			PeerServer.log.warning(e.getMessage());
			invalidProtocolResponse(message.peer, "message must be valid JSON data: " + e.getMessage());
			return;
		}

        // try to respond to the message
        try {
            Result<JSONException, String> command = document.get("command");
            Result<JSONException, String> friendlyName = document.get("friendlyName");

            // if we got a friendly name, log it
            String logMessage = message.peer.getForeignName() + " received: " + command
                    + friendlyName.map(name -> " (via " + name + ")").orElse("");
            PeerServer.log.info(logMessage);
            respondToMessage(message.peer, command.get(), document);
        } catch (JSONException e) {
            PeerServer.log.warning(e.getMessage());
            invalidProtocolResponse(message.peer, e.getMessage());
        }
    }

    /**
     * Respond to the message, after error checking and parsing.
     */

    private void respondToMessage(Peer peer, @NotNull String command, JSONDocument document)
            throws JSONException {
        Maybe<Message> parsedResponse = Maybe.nothing();

        // Look up some data for later: these are used for multiple cases
        Result<JSONException, String> pathName = document.get("pathName");
        Result<JSONException, FileDescriptor> fileDescriptor = FileDescriptor.fromJSON(document);
        Result<JSONException, Long> position = document.get("position");
        Result<JSONException, Long> length = document.get("length");
        Result<JSONException, HostPort> hostPort = document.getJSON("hostPort")
                                                           .andThen(HostPort::fromJSON);

        switch (command) {
            /*
             * File and directory requests
             */
            case Message.FILE_CREATE_REQUEST:
                FileCreateResponse createResponse = new FileCreateResponse(server.getFSManager(), pathName.get(), fileDescriptor.get(), length.get(), false);
                peer.sendMessage(createResponse);
                if (createResponse.successful) {
                    // Check if this file is already elsewhere on disk
                    boolean checkShortcut = true;
                    try {
                        checkShortcut = !server.getFSManager().checkShortcut(pathName.get());
                    } catch (IOException e) {
                        PeerServer.log.severe(peer.getForeignName() + ": error checking shortcut for " + pathName);
                    }

                    if (checkShortcut) {
                        PeerServer.log.info(peer.getForeignName() + ": file " + pathName +
                                " not available locally. Send a FILE_BYTES_REQUEST");
                        rwManager.addFile(peer, pathName.get(), fileDescriptor.get());
                    }
                }
                break;
            case Message.FILE_MODIFY_REQUEST:
                FileModifyResponse modifyResponse = new FileModifyResponse(server.getFSManager(), fileDescriptor.get(), pathName.get(), false);
                peer.sendMessage(modifyResponse);
                if (modifyResponse.successful) {
                    rwManager.addFile(peer, pathName.get(), fileDescriptor.get());
                }
                break;
            case Message.FILE_BYTES_REQUEST:
                rwManager.readFile(peer, fileDescriptor.get(), pathName.get(), position.get(), length.get());
                break;
            case Message.FILE_DELETE_REQUEST:
                peer.sendMessage(new FileDeleteResponse(server.getFSManager(), fileDescriptor.get(), pathName.get(), false));
                break;

            case Message.DIRECTORY_CREATE_REQUEST:
                peer.sendMessage(new DirectoryCreateResponse(server.getFSManager(), pathName.get(), false));
                break;

            case Message.DIRECTORY_DELETE_REQUEST:
                peer.sendMessage(new DirectoryDeleteResponse(server.getFSManager(), pathName.get(), false));
                break;

            /*
             * File and directory responses
             */
            case Message.FILE_CREATE_RESPONSE:
                parsedResponse = Maybe.just(new FileCreateResponse(server.getFSManager(), pathName.get(), fileDescriptor.get(), length.get(),true));
                break;
            case Message.FILE_DELETE_RESPONSE:
                parsedResponse = Maybe.just(new FileDeleteResponse(server.getFSManager(), fileDescriptor.get(), pathName.get(), true));
                break;
            case Message.FILE_MODIFY_RESPONSE:
                parsedResponse = Maybe.just(new FileModifyResponse(server.getFSManager(), fileDescriptor.get(), pathName.get(), true));
                break;
            case Message.DIRECTORY_CREATE_RESPONSE:
                parsedResponse = Maybe.just(new DirectoryCreateResponse(server.getFSManager(), pathName.get(), true));
                break;
            case Message.DIRECTORY_DELETE_RESPONSE:
                parsedResponse = Maybe.just(new DirectoryDeleteResponse(server.getFSManager(), pathName.get(), true));
                break;

            case Message.FILE_BYTES_RESPONSE:
                String content = document.getString("content").get();
                FileBytesResponse bytesResponse = new FileBytesResponse(fileDescriptor.get(),
                                                       pathName.get(),
                                                       length.get(),
                                                       position.get(),
                                                       content, "", true);
                parsedResponse = Maybe.just(bytesResponse);
                if (bytesResponse.successful) {
                    rwManager.writeFile(peer, fileDescriptor.get(), pathName.get(), position.get(), length.get(), content);
                } else {
                    rwManager.sendReadRequest(peer, pathName.get(), fileDescriptor.get(), position.get());
                    PeerServer.log.warning("unsuccessful response: " + document.get("message"));
                    PeerServer.log.info("Retrying byte request for " + pathName);
                    // Let's try to read the bytes again!
                }
                break;

            /*
             * Handshake request and responses
             */
            case Message.HANDSHAKE_REQUEST:
                PeerServer.log.info("Received connection request from " + hostPort.get());

                if (server.getConnection().getOutgoingAddresses().contains(hostPort.get())) {
                    PeerServer.log.warning("Already connected to " + hostPort.get());
                    peer.close();
                } else {
                    // we need to pass the host and port we received, as the socketContainer's data may not be accurate
                    // (since this socket was an accepted connection)

                    // this has to be done here because we don't know the foreign port until now
                    peer.activate(hostPort.get());
                    peer.sendMessage(new HandshakeResponse(server.getHostPort(), false));
                    // synchronise with this peer
                    if (peer.needsRequest()) {
                        server.synchroniseFiles();
                    }
                }
                break;

            case Message.HANDSHAKE_RESPONSE:
                parsedResponse = Maybe.just(new HandshakeResponse(server.getHostPort(), true));

                peer.activate(hostPort.get());
                if (peer.needsResponse()) {
                    server.synchroniseFiles();
                }
                break;

            case Message.CONNECTION_REFUSED:
                if (!peer.needsResponse()) {
                    // why did they send this to us..?
                    invalidProtocolResponse(peer, "unexpected CONNECTION_REFUSED");
                }
                PeerServer.log.warning("Connection refused: " + document.get("message").get());
                peer.close();

                // now try to connect to the provided peer list
                Result<JSONException, List<JSONDocument>> peers = document.getArray("peers");
                for (JSONDocument peerHostPort : peers.get()) {
                    String address = HostPort.fromJSON(peerHostPort).toString();
                    server.getConnection().addPeerAddress(address);
                    PeerServer.log.info("Added peer `" + address + "`");
                    server.getConnection().retryPeers();
                }
                break;

            /*
             * Invalid protocol messages
             */
            case Message.INVALID_PROTOCOL:
                // crap.
                PeerServer.log.severe("Invalid protocol response from "
                        + peer.getForeignName() + ": " + document.getString("message").get());
                peer.close();
                break;

            default:
                invalidProtocolResponse(peer, "unrecognised command `" + command + "`");
                break;
        }
        parsedResponse.consume(response -> {
                // If it's a response other than HANDSHAKE_RESPONSE, make sure it has a status and message field
                if (!response.isRequest() && !command.equals(Message.HANDSHAKE_RESPONSE)) {
                    try {
                        response.reportErrors();
                    } catch (JSONException ignored) {}
                }
                peer.notify(response);
        });
    }

    /**
     * A helper method to send an INVALID_PROTOCOL message.
     */
    private void invalidProtocolResponse(@NotNull Peer peer, String message) {
        peer.sendMessageAndClose(new InvalidProtocol(peer, message));
    }
}
