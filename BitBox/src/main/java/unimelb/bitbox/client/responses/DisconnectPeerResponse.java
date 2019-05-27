package unimelb.bitbox.client.responses;

import unimelb.bitbox.peers.PeerConnection;
import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.util.network.HostPort;

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

    protected DisconnectPeerResponse(ServerMain server, HostPort hostPort) {
        response.append("command", "DISCONNECT_PEER_RESPONSE");

        final String SUCCESS = "disconnected from peer";
        String reply = SUCCESS;

        // ELEANOR: Better to use an if statement than exception handling for a simple branch
        PeerConnection peer = server.getConnection().getPeer(hostPort);
        if (peer == null) {
            reply = "connection not active";
        } else {
            server.getConnection().closeConnection(peer);
        }

        response.append("host", hostPort.hostname);
        response.append("port", hostPort.port);
        response.append("status", reply == SUCCESS);
        response.append("message", reply);
    }

}
