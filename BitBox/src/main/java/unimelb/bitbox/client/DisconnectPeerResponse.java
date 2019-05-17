package unimelb.bitbox.client;

import unimelb.bitbox.PeerConnection;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.ResponseFormatException;

public class DisconnectPeerResponse extends IPeerResponse{

    protected DisconnectPeerResponse(ServerMain server, JsonDocument document) throws ResponseFormatException {
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
            // When a peer is not active or we cannot find a peer in our active peer list
            // because "localhost" is being stored as "127.0.0.1"
            // as Bitbox does not store peers' IP addresses instead - existing design issue
            // so we just have to cope with it
            reply = "connection not active";
            e.printStackTrace();
        }

        response.append("host", host);
        response.append("port", port);
        response.append("status", reply == SUCCESS);
        response.append("message", reply);
    }

}
