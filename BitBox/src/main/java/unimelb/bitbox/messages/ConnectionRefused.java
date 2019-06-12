package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;

public class ConnectionRefused extends Message {
    public ConnectionRefused(String message) {
        super("REFUSED");
        document.append("command", MessageType.CONNECTION_REFUSED);
        document.append("message", message);
        document.join(PeerServer.connection());
    }
}
