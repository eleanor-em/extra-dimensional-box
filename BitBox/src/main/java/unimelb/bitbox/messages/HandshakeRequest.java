package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;

public class HandshakeRequest extends Message {

    public HandshakeRequest() {
        super("HANDSHAKE");
        document.append("command", MessageType.HANDSHAKE_REQUEST);

        document.append("hostPort", PeerServer.getHostPort().toJSON());
    }
}
