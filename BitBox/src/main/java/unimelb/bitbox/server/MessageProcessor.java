package unimelb.bitbox.server;

import functional.algebraic.Maybe;
import functional.algebraic.Result;
import org.jetbrains.annotations.NotNull;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.network.FilePacket;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The message processor collects messages from the various PeerConnections, and then responds appropriately.
 *
 * @author Eleanor McMurtry
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
                PeerServer.log().warning("receiving thread interrupted");
                e.printStackTrace();
            }
        }
    }

	/**
	 * Perform error checking, and send appropriate reply messages.
	 */
	private void processMessage(@NotNull ReceivedMessage message) {
		String text = message.text;
        // try to respond to the message
        try {
            var doc = JSONDocument.parse(text).get();
            String command = doc.getString("command").get();
            var friendlyName = doc.getString("friendlyName");

            // if we got a friendly name, log it
            String logMessage = message.peer.getForeignName() + " received: " + command
                    + friendlyName.map(name -> " (via " + name + ")").orElse("");
            PeerServer.log().fine(logMessage);
            PeerServer.log().fine(doc.toString());

            respondToMessage(message.peer, MessageType.fromString(command).get(), doc);
        } catch (JSONException e) {
            PeerServer.log().warning(e.getMessage());
            invalidProtocolResponse(message.peer, e.getMessage());
        }
    }

    /**
     * Respond to the message, after error checking and parsing.
     */

    private void respondToMessage(Peer peer, MessageType command, JSONDocument document)
            throws JSONException {
        Maybe<Message> parsedResponse = Maybe.nothing();

        // Look up the data for each handler. These are only used if required for the specific handler
        var pathName = document.getString("pathName");
        var fileDescriptor = pathName.andThen(name -> document.getJSON("fileDescriptor")
                                                              .andThen(fd -> FileDescriptor.fromJSON(name, fd)));
        var position = document.getLong("position");
        var length = document.getLong("length");
        var packet = fileDescriptor.andThen(fd ->
              position.andThen(pos ->
              length.map(len -> new FilePacket(peer, fd, pos, len))));
        var content = document.getString("content");
        var hostPort = document.getJSON("hostPort").andThen(HostPort::fromJSON);

        switch (command) {
            /* Trivial requests */
            case FILE_CREATE_REQUEST:
                peer.sendMessage(new FileCreateResponse(fileDescriptor.get(), peer));
                break;
            case FILE_MODIFY_REQUEST:
                peer.sendMessage(new FileModifyResponse(fileDescriptor.get(), peer));
                break;
            case FILE_BYTES_REQUEST:
                PeerServer.rwManager().readFile(packet.get());
                break;
            case FILE_DELETE_REQUEST:
                peer.sendMessage(new FileDeleteResponse(fileDescriptor.get(), peer));
                break;
            case DIRECTORY_CREATE_REQUEST:
                peer.sendMessage(new DirectoryCreateResponse(pathName.get(), peer));
                break;
            case DIRECTORY_DELETE_REQUEST:
                peer.sendMessage(new DirectoryDeleteResponse(pathName.get(), peer));
                break;
            /* Trivial responses */
            case FILE_CREATE_RESPONSE:
                parsedResponse = Maybe.just(new FileCreateResponse(fileDescriptor.get(), peer));
                break;
            case FILE_DELETE_RESPONSE:
                parsedResponse = Maybe.just(new FileDeleteResponse(fileDescriptor.get(), peer));
                break;
            case FILE_MODIFY_RESPONSE:
                parsedResponse = Maybe.just(new FileModifyResponse(fileDescriptor.get(), peer));
                break;
            case DIRECTORY_CREATE_RESPONSE:
                parsedResponse = Maybe.just(new DirectoryCreateResponse(pathName.get(), peer));
                break;
            case DIRECTORY_DELETE_RESPONSE:
                parsedResponse = Maybe.just(new DirectoryDeleteResponse(pathName.get(), peer));
                break;
            case HANDSHAKE_RESPONSE:
                parsedResponse = Maybe.just(new HandshakeResponse(peer, hostPort.get()));

                if (peer.needsResponse()) {
                    peer.activate(hostPort.get());

                    PeerServer.log().fine(peer + ": sending synchronisation requests");
                    PeerServer.synchroniseFiles(peer);
                }
                break;

            // Write the received bytes, if we're downloading the file
            case FILE_BYTES_RESPONSE:
                final FileBytesResponse response = new FileBytesResponse(packet.get());
                parsedResponse = Maybe.just(response);

                if (PeerServer.fsManager().fileLoading(fileDescriptor.get())) {
                    if (document.getBoolean("status").get()) {
                        PeerServer.rwManager().writeFile(packet.get(), content.get());
                    } else if (document.getBoolean("retry").orElse(false)) {
                        // If the request failed for a random reason, let's request the bytes again!
                        PeerServer.log().fine("retrying byte request for " + pathName);
                        peer.sendMessage(FileBytesRequest.retry(response));
                    } else {
                        // If the request faRiled for a permanent reason, just give up for now
                        PeerServer.rwManager().cancelFile(fileDescriptor.get());
                    }
                }
                break;

            // If we get a handshake request, check this is a new connection
            case HANDSHAKE_REQUEST:
                PeerServer.log().fine("received connection request from " + hostPort.get());

                if (PeerServer.connection().getPeer(hostPort.get())
                                           .map(existing -> peer != existing)
                                           .orElse(false)) {
                    PeerServer.log().warning("already connected to " + hostPort.get());
                    peer.close();
                } else {
                    PeerServer.log().fine("responding to " + hostPort.get());
                    peer.sendMessage(new HandshakeResponse(peer, hostPort.get()));

                    PeerServer.synchroniseFiles(peer);
                }
                break;

            case CONNECTION_REFUSED:
                if (!peer.needsResponse()) {
                    // why did they send this to us..?
                    invalidProtocolResponse(peer, "unexpected CONNECTION_REFUSED");
                }
                PeerServer.log().warning("connection refused: " + document.getString("message").get());
                peer.close();

                // now try to connect to the provided peer list
                Result<List<JSONDocument>, JSONException> peers = document.getJSONArray("peers");
                for (JSONDocument peerHostPort : peers.get()) {
                    HostPort.fromJSON(peerHostPort)
                            .ifOk(address -> {
                                PeerServer.connection().addPeerAddress(address);
                                PeerServer.log().fine("Added peer `" + address + "`");
                            });
                    PeerServer.connection().retryPeers();
                }
                break;

            case INVALID_PROTOCOL:
                PeerServer.log().severe("invalid protocol response from "
                        + peer.getForeignName() + ": " + document.getString("message").get());
                peer.close();
                break;

            default:
                invalidProtocolResponse(peer, "unrecognised command `" + command + "`");
                break;
        }
        parsedResponse.consume(response -> {
                // If it's a response other than HANDSHAKE_RESPONSE, make sure it has a status and message field
                if (!response.isRequest() && command != MessageType.HANDSHAKE_RESPONSE) {
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
