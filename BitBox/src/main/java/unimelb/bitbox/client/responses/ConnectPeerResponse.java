package unimelb.bitbox.client.responses;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.ResponseFormatException;

/**
 * Generates the message content of CONNECT_PEER
 * to be sent by a Peer to a Client.
 *
 * Known issue in the design:
 * due to the design issue of Bitbox,
 * host name "localhost" may sometimes be stored as "127.0.0.1".<br/>
 *
 * For example, if a peer tries to connect to "localhost:8114" and
 * the peer was stored as "127.0.0.1:8114", the connection would succeed
 * given the existing constraints.
 */
class ConnectPeerResponse extends ClientResponse {

    protected ConnectPeerResponse(ServerMain server, JsonDocument document) throws ResponseFormatException {
        response.append("command", "CONNECT_PEER_RESPONSE");

        String host = document.require("host");
        int port = (int)(long) document.require("port");
        final String SUCCESS = "connected to peer";
        String reply = SUCCESS;

        // ELEANOR: Server should handle incoming peer count, not this response object.
        if (!server.clientTryPeer(host, port)){
            // failed if the target peer is offline/not available
            ServerMain.log.warning("target peer is not reachable. Failed to connect to " +
                    host + ":" + port);
                    reply = "connection failed";
        }

        response.append("host", host);
        response.append("port", port);
        response.append("status", reply == SUCCESS);
        response.append("message", reply);
    }

}
