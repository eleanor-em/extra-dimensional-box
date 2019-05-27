package unimelb.bitbox.messages;

import unimelb.bitbox.util.network.HostPort;

public class HandshakeResponse extends Message {
    public HandshakeResponse(HostPort hostPort, boolean dryRun) {
        super("HANDSHAKE");
        if (dryRun) {
            return;
        }
        document.append("command", HANDSHAKE_RESPONSE);
        document.append("hostPort", hostPort.toJSON());
    }
}
