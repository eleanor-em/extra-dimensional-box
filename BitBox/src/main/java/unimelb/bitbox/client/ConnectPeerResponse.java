package unimelb.bitbox.client;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.ResponseFormatException;

/**
 * ConnectPeerResponse generates the message content in response to
 * the connect_peer request sent by a client.
 */
public class ConnectPeerResponse extends IPeerResponse {

    protected ConnectPeerResponse(ServerMain server, JsonDocument document) throws ResponseFormatException {
        super(server, document);

        response.append("command", "CONNECT_PEER_RESPONSE");

        String host = document.require("host");
        int port = (int)(long) document.require("port");
        final String SUCCESS = "connected to peer";
        String reply = SUCCESS;
        if (!server.tryPeer(host, port)){
            // failed if maximumIncommingConnections is reached or the target peer is offline
            reply = "connection failed";
        }
        response.append("host", host);
        response.append("port", port);
        response.append("status", reply == SUCCESS);
        response.append("message", reply);
    }

}
