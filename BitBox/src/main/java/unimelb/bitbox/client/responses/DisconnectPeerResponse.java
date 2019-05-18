package unimelb.bitbox.client.responses;

import unimelb.bitbox.PeerConnection;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.ResponseFormatException;

/**
 * Generates the message content of DISCONNECT_PEER_RESPONSE
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
public class DisconnectPeerResponse extends ClientResponse {

    public DisconnectPeerResponse(ServerMain server, JsonDocument document) throws ResponseFormatException {
        super(server, document);

        response.append("command", "DISCONNECT_PEER_RESPONSE");

        String host = document.require("host");
        int port = (int)(long) document.require("port");
        final String SUCCESS = "disconnected from peer";
        String reply = SUCCESS;

        try{
            PeerConnection peer = server.getPeer(host, port);
            server.closeConnection(peer);
        }
        catch (NullPointerException e){
            // The design is not good. Host names are stored as IP addresses sometime. Unpredictable.
            // If localhost is stored as IP address, cancelling by the alias will fail!
            reply = "connection not active";
            e.printStackTrace();
        }

        response.append("host", host);
        response.append("port", port);
        response.append("status", reply == SUCCESS);
        response.append("message", reply);
    }

}
