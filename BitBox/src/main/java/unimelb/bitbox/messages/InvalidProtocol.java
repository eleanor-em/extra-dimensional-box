package unimelb.bitbox.messages;

import unimelb.bitbox.peers.PeerConnection;
import unimelb.bitbox.server.ServerMain;

public class InvalidProtocol extends Message {
    public InvalidProtocol(PeerConnection peer, String message) {
        super("INVALID");
        ServerMain.log.warning("Sending invalid protcool to " + peer.getForeignName() + ": " + message);
        document.append("command", INVALID_PROTOCOL);
        document.append("message", message);
    }
}
