package unimelb.bitbox.client.responses;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.ResponseFormatException;

/**
 * Generates the message content of CONNECT_PEER_RESPONSE
 * to be sent by a Peer to a Client.
 */
public class ConnectPeerResponse extends IClientResponse {

    public ConnectPeerResponse(ServerMain server, JsonDocument document) throws ResponseFormatException {
        super(server, document);

        response.append("command", "CONNECT_PEER_RESPONSE");

        String host = document.require("host");
        int port = (int)(long) document.require("port");
        final String SUCCESS = "connected to peer";
        String reply = SUCCESS;

        // check we have room for more peers
        // (only count incoming connections)
        if (server.getIncomingPeerCount() >= Integer.parseInt(
                Configuration.getConfigurationValue("maximumIncommingConnections"))){
            // failed if maximumIncommingConnections is reached
            reply = "connection failed";
        } else if (!server.tryPeer(host, port)){
            // failed if the target peer is offline/not available
            reply = "connection failed";
        }
        response.append("host", host);
        response.append("port", port);
        response.append("status", reply == SUCCESS);
        response.append("message", reply);
    }

}
