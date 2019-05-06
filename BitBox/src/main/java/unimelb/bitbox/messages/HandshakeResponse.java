package unimelb.bitbox.messages;

import unimelb.bitbox.util.Document;

public class HandshakeResponse extends Message {
    public HandshakeResponse(String host, int port) {
        document.append("command", HANDSHAKE_RESPONSE);

        Document hostPort = new Document();
        hostPort.append("host", host);
        hostPort.append("port", port);

        document.append("hostPort", hostPort);
    }
}
