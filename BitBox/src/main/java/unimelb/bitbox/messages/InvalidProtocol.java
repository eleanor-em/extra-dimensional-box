package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;

/**
 * INVALID_PROTOCOL message.
 *
 * @author Eleanor McMurtry
 */
public class InvalidProtocol extends Message {
    public InvalidProtocol(Peer peer, String message) {
        super("INVALID");
        PeerServer.log().warning("Sending invalid protocol to " + peer.getForeignName() + ": " + message);
        document.append("command", MessageType.INVALID_PROTOCOL);
        document.append("message", message);
    }
}
