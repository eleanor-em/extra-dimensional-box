package unimelb.bitbox.messages;

import unimelb.bitbox.util.network.HostPort;

public class HandshakeRequest extends Message {

    public HandshakeRequest(HostPort hostPort) {
        super("HANDSHAKE");
        document.append("command", HANDSHAKE_REQUEST);

        document.append("hostPort", hostPort.toJSON());
    }
}
