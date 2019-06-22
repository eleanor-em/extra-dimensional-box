package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;

/**
 * HANDSHAKE_REQUEST message.
 *
 * @author Eleanor McMurtry
 */
public class HandshakeRequest extends Message {
    public HandshakeRequest() {
        super("HANDSHAKE");
        document.append("command", MessageType.HANDSHAKE_REQUEST);
        document.append("hostPort", PeerServer.hostPort().toJSON());
        document.append("pubKey", PeerServer.groupManager().getPubKey().toString());
    }
}
