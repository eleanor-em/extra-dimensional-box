package unimelb.bitbox.messages;

import unimelb.bitbox.util.config.Configuration;
import unimelb.bitbox.util.network.HostPort;


/**
 * HANDSHAKE_REQUEST message.
 *
 * @author Eleanor McMurtry
 */
public class HandshakeRequest extends Message {
    public HandshakeRequest() {
        super("HANDSHAKE");
        document.append("command", MessageType.HANDSHAKE_REQUEST);
        document.append("hostPort", new HostPort(Configuration.getAdvertisedName(),
                                                      Configuration.getPort()).toJSON());
    }
}
