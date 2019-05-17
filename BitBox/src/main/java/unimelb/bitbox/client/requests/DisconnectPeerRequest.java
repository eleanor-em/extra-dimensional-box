package unimelb.bitbox.client.requests;

/**
 * Prepare the DISCONNECT_PEER_REQUEST to be sent by the Client to a Peer.
 */
public class DisconnectPeerRequest extends IClientRequest {
    public DisconnectPeerRequest(String peerAddress) throws IllegalArgumentException {
        super("DISCONNECT_PEER_REQUEST", peerAddress);
    }
}
