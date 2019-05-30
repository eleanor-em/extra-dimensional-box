package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;

public class InvalidProtocol extends Message {
    public InvalidProtocol(Peer peer, String message) {
        super("INVALID");
        PeerServer.logWarning("Sending invalid protcool to " + peer.getForeignName() + ": " + message);
        document.append("command", INVALID_PROTOCOL);
        document.append("message", message);
    }
}
