package unimelb.bitbox.client.requests;

/**
 * Prepare the DISCONNECT_PEER_REQUEST to be sent by the Client to a Peer.
 *
 * @author Eleanor McMurtry
 * @author Andrea Law
 */
class DisconnectPeerRequest extends ClientRequest {
    DisconnectPeerRequest(String peerAddress) throws ClientArgsException {
        super("DISCONNECT_PEER_REQUEST", peerAddress);
    }
}
