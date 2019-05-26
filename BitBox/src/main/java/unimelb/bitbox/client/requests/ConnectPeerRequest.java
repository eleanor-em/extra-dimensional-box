package unimelb.bitbox.client.requests;

import unimelb.bitbox.util.network.HostPortParseException;

/**
 * Prepare the CONNECT_PEER_REQUEST to be sent by the Client to a Peer.
 */
public class ConnectPeerRequest extends ClientRequest {
    public ConnectPeerRequest(String peerAddress) throws HostPortParseException  {
        super("CONNECT_PEER_REQUEST", peerAddress);
    }
}
