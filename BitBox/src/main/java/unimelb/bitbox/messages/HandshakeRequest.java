package unimelb.bitbox.messages;

import unimelb.bitbox.server.ServerMain;

public class HandshakeRequest extends Message {

    public HandshakeRequest() {
        super("HANDSHAKE");
        document.append("command", HANDSHAKE_REQUEST);

        document.append("hostPort", ServerMain.getHostPort().toJSON());
    }
}
