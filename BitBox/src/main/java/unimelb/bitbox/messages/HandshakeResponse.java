package unimelb.bitbox.messages;

import unimelb.bitbox.util.JsonDocument;

public class HandshakeResponse extends Message {
    public HandshakeResponse(String host, long port, boolean dryRun) {
        super("HANDSHAKE");
        if (dryRun) {
            return;
        }
        document.append("command", HANDSHAKE_RESPONSE);

        JsonDocument hostPort = new JsonDocument();
        hostPort.append("host", host);
        hostPort.append("port", port);

        document.append("hostPort", hostPort);
    }
}
