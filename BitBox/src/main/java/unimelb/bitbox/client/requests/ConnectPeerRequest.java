package unimelb.bitbox.client.requests;

/**
 * Prepare the CONNECT_PEER_REQUEST to be sent by the Client to a Peer.
 *
 * @author Eleanor McMurtry
 * @author Andrea Law
 */
class ConnectPeerRequest extends ClientRequest {
    ConnectPeerRequest(String peerAddress) throws ClientArgsException  {
        super("CONNECT_PEER_REQUEST", peerAddress);
    }
}
