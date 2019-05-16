package unimelb.bitbox.messages;

import unimelb.bitbox.util.Document;

public class HandshakeRequest extends Message {

    public HandshakeRequest(String host, int port) {
        document.append("command", HANDSHAKE_REQUEST);

        Document hostPort = new Document();
        hostPort.append("host", host);
        hostPort.append("port", port);

        document.append("hostPort", hostPort);
    }
}
