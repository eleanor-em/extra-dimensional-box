package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;

public class InvalidProtocol extends Message {
    public InvalidProtocol(Peer peer, String message) {
        super("INVALID");
        PeerServer.log().warning("Sending invalid protcool to " + peer.getForeignName() + ": " + message);
        document.append("command", MessageType.INVALID_PROTOCOL);
        document.append("message", message);
    }
}
