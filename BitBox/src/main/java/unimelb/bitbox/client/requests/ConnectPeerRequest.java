package unimelb.bitbox.client.requests;

/**
 * Prepare the CONNECT_PEER_REQUEST to be sent by the Client to a Peer.
 */
public class ConnectPeerRequest extends ClientRequest {
    public ConnectPeerRequest(String peerAddress) throws ClientArgsException  {
        super("CONNECT_PEER_REQUEST", peerAddress);
    }
}
