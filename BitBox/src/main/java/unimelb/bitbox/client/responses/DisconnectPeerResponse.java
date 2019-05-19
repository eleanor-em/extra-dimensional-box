package unimelb.bitbox.client.responses;

import unimelb.bitbox.PeerConnection;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.ResponseFormatException;

/**
 * Generates the message content of DISCONNECT_PEER
 * to be sent by a Peer to a Client.
 *
 * Known issue in the design:
 * due to the design issue of Bitbox,
 * host name "localhost" may sometimes be stored as "127.0.0.1".<br/>
 *
 * For example, if a peer tries to disconnect from "localhost:8114" and
 * the peer was stored as "127.0.0.1:8114", the attempt would fail
 * given the existing constraints.
 */
class DisconnectPeerResponse extends ClientResponse {

    protected DisconnectPeerResponse(ServerMain server, JsonDocument document) throws ResponseFormatException {
        response.append("command", "DISCONNECT_PEER_RESPONSE");

        String host = document.require("host");
        int port = (int)(long) document.require("port");
        final String SUCCESS = "disconnected from peer";
        String reply = SUCCESS;

        // ELEANOR: Better to use an if statement than exception handling for a simple branch
        PeerConnection peer = server.getPeer(host, port);
        if (peer == null) {
            // The design is not good. Host names are stored as IP addresses sometime. Unpredictable.
            // If localhost is stored as IP address, cancelling by the alias will fail!
            //
            // ELEANOR: This just means we'd need to add a DNS resolution step in practice.
            //          For now, requiring servers & clients to only use IP addresses is fine.
            reply = "connection not active";
        } else {
            server.closeConnection(peer);
        }

        response.append("host", host);
        response.append("port", port);
        response.append("status", reply == SUCCESS);
        response.append("message", reply);
    }

}
