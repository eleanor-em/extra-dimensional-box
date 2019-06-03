package unimelb.bitbox.server;

import org.jetbrains.annotations.NotNull;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.functional.algebraic.Maybe;
import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The message processor collects messages from the various PeerConnections, and then responds appropriately.
 */
public class MessageProcessor implements Runnable  {
    private final BlockingQueue<ReceivedMessage> messages = new LinkedBlockingQueue<>();

    public void add(ReceivedMessage message) {
        messages.add(message);
    }

    @Override
    public void run() {
        while (true) {
            try {
                processMessage(messages.take());
            } catch (InterruptedException e) {
                PeerServer.logWarning("receiving thread interrupted");
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
			PeerServer.logWarning(e.getMessage());
			invalidProtocolResponse(message.peer, "message must be valid JSON data: " + e.getMessage());
			return;
		}

        // try to respond to the message
        try {
            Result<JSONException, String> command = document.get("command");
            Result<JSONException, String> friendlyName = document.get("friendlyName");

            // if we got a friendly name, log it
            String logMessage = message.peer.getForeignName() + " received: " + command.get()
                    + friendlyName.map(name -> " (via " + name + ")").orElse("");
            PeerServer.logInfo(logMessage);
            respondToMessage(message.peer, command.get(), document);
        } catch (JSONException e) {
            PeerServer.logWarning(e.getMessage());
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
        Result<JSONException, FileDescriptor> fileDescriptor = document.getJSON("fileDescriptor")
                                                                       .andThen(FileDescriptor::fromJSON);
        Result<JSONException, Long> position = document.get("position");
        Result<JSONException, Long> length = document.get("length");
        Result<JSONException, HostPort> hostPort = document.getJSON("hostPort")
                                                           .andThen(HostPort::fromJSON);

        switch (command) {
            /*
             * File and directory requests
             */
            case Message.FILE_CREATE_REQUEST:
                FileCreateResponse createResponse = new FileCreateResponse(pathName.get(), fileDescriptor.get(), peer);
                peer.sendMessage(createResponse);
                break;
            case Message.FILE_MODIFY_REQUEST:
                FileModifyResponse modifyResponse = new FileModifyResponse(pathName.get(), fileDescriptor.get(), peer);
                peer.sendMessage(modifyResponse);
                break;
            case Message.FILE_BYTES_REQUEST:
                PeerServer.rwManager().readFile(peer, pathName.get(), fileDescriptor.get(), position.get(), length.get());
                break;
            case Message.FILE_DELETE_REQUEST:
                peer.sendMessage(new FileDeleteResponse(pathName.get(), fileDescriptor.get(), peer));
                break;

            case Message.DIRECTORY_CREATE_REQUEST:
                peer.sendMessage(new DirectoryCreateResponse(pathName.get(), peer));
                break;

            case Message.DIRECTORY_DELETE_REQUEST:
                peer.sendMessage(new DirectoryDeleteResponse(pathName.get(), peer));
                break;

            /*
             * File and directory responses
             */
            case Message.FILE_CREATE_RESPONSE:
                parsedResponse = Maybe.just(new FileCreateResponse(pathName.get(), fileDescriptor.get(), peer));
                break;
            case Message.FILE_DELETE_RESPONSE:
                parsedResponse = Maybe.just(new FileDeleteResponse(pathName.get(), fileDescriptor.get(), peer));
                break;
            case Message.FILE_MODIFY_RESPONSE:
                parsedResponse = Maybe.just(new FileModifyResponse(pathName.get(), fileDescriptor.get(), peer));
                break;
            case Message.DIRECTORY_CREATE_RESPONSE:
                parsedResponse = Maybe.just(new DirectoryCreateResponse(pathName.get(), peer));
                break;
            case Message.DIRECTORY_DELETE_RESPONSE:
                parsedResponse = Maybe.just(new DirectoryDeleteResponse(pathName.get(), peer));
                break;

            case Message.FILE_BYTES_RESPONSE:
                String content = document.getString("content").get();
                FileBytesResponse bytesResponse = new FileBytesResponse(pathName.get(), fileDescriptor.get(), length.get(),
                                                                        position.get(), content, FileBytesResponse.SUCCESS, peer);
                parsedResponse = Maybe.just(bytesResponse);
                break;

            /*
             * Handshake request and responses
             */
            case Message.HANDSHAKE_REQUEST:
                PeerServer.logInfo("Received connection request from " + hostPort.get());

                if (PeerServer.getConnection().getOutgoingAddresses().contains(hostPort.get())) {
                    PeerServer.logWarning("Already connected to " + hostPort.get());
                    peer.close();
                } else {
                    // we need to pass the host and port we received, as the socketContainer's data may not be accurate
                    // (since this socket was an accepted connection)

                    // this has to be done here because we don't know the foreign port until now
                    peer.sendMessage(new HandshakeResponse(peer, hostPort.get()));
                }
                break;

            case Message.HANDSHAKE_RESPONSE:
                parsedResponse = Maybe.just(new HandshakeResponse(peer, hostPort.get()));
                break;

            case Message.CONNECTION_REFUSED:
                if (!peer.needsResponse()) {
                    // why did they send this to us..?
                    invalidProtocolResponse(peer, "unexpected CONNECTION_REFUSED");
                }
                PeerServer.logWarning("Connection refused: " + document.get("message").get());
                peer.close();

                // now try to connect to the provided peer list
                Result<JSONException, List<JSONDocument>> peers = document.getArray("peers");
                for (JSONDocument peerHostPort : peers.get()) {
                    HostPort.fromJSON(peerHostPort)
                            .ok(address -> {
                                PeerServer.getConnection().addPeerAddress(address);
                                PeerServer.logInfo("Added peer `" + address + "`");
                            });
                    PeerServer.getConnection().retryPeers();
                }
                break;

            /*
             * Invalid protocol messages
             */
            case Message.INVALID_PROTOCOL:
                // crap.
                PeerServer.logSevere("Invalid protocol response from "
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
                    response.reportErrors();
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
