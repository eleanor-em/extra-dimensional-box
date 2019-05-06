package unimelb.bitbox.messages;

import unimelb.bitbox.PeerConnection;
import unimelb.bitbox.util.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConnectionRefused extends Message {
    public ConnectionRefused(List<PeerConnection> peers) {
        document.append("command", CONNECTION_REFUSED);
        document.append("message", "connection limit reached");
        ArrayList<Document> peersDoc = peers.stream().map(peer -> {
            Document doc = new Document();
            doc.append("host", peer.getHost());
            doc.append("port", peer.getPort());
            return doc;
        }).collect(Collectors.toCollection(ArrayList::new));
        document.append("peers", peersDoc);
    }
}
