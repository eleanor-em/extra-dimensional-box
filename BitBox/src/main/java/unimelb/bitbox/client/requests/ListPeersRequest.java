package unimelb.bitbox.client.requests;

/**
 * Prepare the LIST_PEERS_REQUEST to be sent by the Client to a Peer.
 */
class ListPeersRequest extends ClientRequest {
    ListPeersRequest() {
        super("LIST_PEERS_REQUEST");
    }
}