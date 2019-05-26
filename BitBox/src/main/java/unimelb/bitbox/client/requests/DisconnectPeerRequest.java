package unimelb.bitbox.client.requests;

import unimelb.bitbox.util.network.HostPortParseException;

/**
 * Prepare the DISCONNECT_PEER_REQUEST to be sent by the Client to a Peer.
 */
public class DisconnectPeerRequest extends ClientRequest {
    public DisconnectPeerRequest(String peerAddress) throws HostPortParseException {
        super("DISCONNECT_PEER_REQUEST", peerAddress);
    }
}
