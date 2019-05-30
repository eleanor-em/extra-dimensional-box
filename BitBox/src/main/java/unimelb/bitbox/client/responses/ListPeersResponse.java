package unimelb.bitbox.client.responses;

import unimelb.bitbox.server.PeerServer;

import java.util.stream.Collectors;

/**
 * Generates the message content of LIST_PEERS
 * to be sent by a Peer to a Client.
 */
class ListPeersResponse extends ClientResponse {

    protected ListPeersResponse() {
        response.append("command", "LIST_PEERS_RESPONSE");
        response.append("peers", PeerServer.getConnection().getActivePeers()
                                            .stream()
                                            .map(peer -> peer.getHostPort().toJSON())
                                            .collect(Collectors.toList()));
    }

}
