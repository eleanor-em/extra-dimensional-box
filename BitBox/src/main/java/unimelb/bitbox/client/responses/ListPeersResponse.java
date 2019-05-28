package unimelb.bitbox.client.responses;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.network.JSONDocument;

import java.util.ArrayList;

/**
 * Generates the message content of LIST_PEERS
 * to be sent by a Peer to a Client.
 */
class ListPeersResponse extends ClientResponse {

    protected ListPeersResponse(PeerServer server) {
        response.append("command", "LIST_PEERS_RESPONSE");

        // add all peers currently connected to and previously
        // connected to by this peer
        ArrayList<JSONDocument> peers = new ArrayList<>();
        for (Peer peer : server.getConnection().getActivePeers()){
            peers.add(peer.getHostPort().toJSON());
        }
        response.append("peers", peers);
    }

}
