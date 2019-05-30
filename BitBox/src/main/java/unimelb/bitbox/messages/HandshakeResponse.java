package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;

public class HandshakeResponse extends Response {
    public HandshakeResponse() {
        super("HANDSHAKE");
        document.append("command", HANDSHAKE_RESPONSE);
        document.append("hostPort", PeerServer.getHostPort().toJSON());
    }

    @Override
    void onSent() {}
}
