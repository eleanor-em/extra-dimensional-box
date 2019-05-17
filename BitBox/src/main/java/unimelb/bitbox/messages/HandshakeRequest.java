package unimelb.bitbox.messages;

import unimelb.bitbox.util.JsonDocument;

public class HandshakeRequest extends Message {

    public HandshakeRequest(String host, int port) {
        super("HANDSHAKE");
        document.append("command", HANDSHAKE_REQUEST);

        JsonDocument hostPort = new JsonDocument();
        hostPort.append("host", host);
        hostPort.append("port", port);

        document.append("hostPort", hostPort);
    }
}
