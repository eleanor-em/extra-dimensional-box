package unimelb.bitbox.client.responses;

import unimelb.bitbox.server.PeerServer;

import java.util.stream.Collectors;

/**
 * Generates the message content of LIST_PEERS
 * to be sent by a Peer to a Client.
 *
 * @author Eleanor McMurtry
 * @author Andrea Law
 */
class ListPeersResponse extends ClientResponse {

    ListPeersResponse() {
        response.append("command", "LIST_PEERS_RESPONSE");
        response.append("peers", PeerServer.connection().getActivePeers()
                                            .stream()
                                            .map(peer -> peer.getHostPort().toJSON())
                                            .collect(Collectors.toList()));
    }

}
