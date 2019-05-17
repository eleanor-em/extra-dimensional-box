package unimelb.bitbox.client;

import unimelb.bitbox.PeerConnection;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;

import java.util.ArrayList;

public class ListPeerResponse extends IPeerResponse{

    protected ListPeerResponse(ServerMain server, JsonDocument document) {
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
