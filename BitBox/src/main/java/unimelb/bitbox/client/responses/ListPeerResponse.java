package unimelb.bitbox.client.responses;

import unimelb.bitbox.PeerConnection;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;

import java.util.ArrayList;

/**
 * Generates the message content of LIST_PEERS_RESPONSE
 * to be sent by a Peer to a Client.
 */
public class ListPeerResponse extends ClientResponse {

    public ListPeerResponse(ServerMain server, JsonDocument document) {
        super(server, document);

        response.append("command", "LIST_PEERS_RESPONSE");

        // add all peers currently connected to and previously
        // connected to by this peer
        ArrayList<JsonDocument> peers = new ArrayList<>();
        for (PeerConnection peer: server.getPeers()){
            JsonDocument peerItem = new JsonDocument();
            peerItem.append("host", peer.getHost());
            peerItem.append("port", peer.getPort());
            peers.add(peerItem);
        }
        response.append("peers", peers);
    }

}
