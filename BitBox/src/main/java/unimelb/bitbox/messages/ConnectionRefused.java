package unimelb.bitbox.messages;

import unimelb.bitbox.peers.PeerConnection;
import unimelb.bitbox.util.network.JSONDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class ConnectionRefused extends Message {
    public ConnectionRefused(Collection<PeerConnection> peers) {
        super(CONNECTION_REFUSED);
        document.append("command", CONNECTION_REFUSED);
        document.append("message", "connection limit reached");
        ArrayList<JSONDocument> peersDoc = peers.stream()
                                                .map(peer -> peer.getHostPort().toJSON())
                                                .collect(Collectors.toCollection(ArrayList::new));
        document.append("peers", peersDoc);
    }
}
