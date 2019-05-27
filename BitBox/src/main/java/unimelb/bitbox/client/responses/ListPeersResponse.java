package unimelb.bitbox.client.responses;

import unimelb.bitbox.peers.PeerConnection;
import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.util.network.JSONDocument;

import java.util.ArrayList;

/**
 * Generates the message content of LIST_PEERS
 * to be sent by a Peer to a Client.
 */
class ListPeersResponse extends ClientResponse {

    protected ListPeersResponse(ServerMain server) {
        response.append("command", "LIST_PEERS_RESPONSE");

        // add all peers currently connected to and previously
        // connected to by this peer
        ArrayList<JSONDocument> peers = new ArrayList<>();
        for (PeerConnection peer : server.getConnection().getActivePeers()){
            peers.add(peer.getHostPort().toJSON());
        }
        response.append("peers", peers);
    }

}
