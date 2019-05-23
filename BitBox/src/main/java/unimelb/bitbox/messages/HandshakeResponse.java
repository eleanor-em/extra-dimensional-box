package unimelb.bitbox.messages;

import unimelb.bitbox.ServerMain;

public class HandshakeResponse extends Message {
    public HandshakeResponse(boolean dryRun) {
        super("HANDSHAKE");
        if (dryRun) {
            return;
        }
        document.append("command", HANDSHAKE_RESPONSE);
        document.append("hostPort", ServerMain.getHostPort().toJSON());
    }
}
