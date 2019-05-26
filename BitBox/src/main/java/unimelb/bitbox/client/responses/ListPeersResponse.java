package unimelb.bitbox.client.responses;

import unimelb.bitbox.PeerConnection;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.network.JsonDocument;

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
        ArrayList<JsonDocument> peers = new ArrayList<>();
        for (PeerConnection peer : server.getActivePeers()){
            peers.add(peer.getHostPort().toJSON());
        }
        response.append("peers", peers);
    }

}
