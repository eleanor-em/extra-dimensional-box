package unimelb.bitbox.client.responses;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.network.HostPort;

/**
 * Generates the message content of DISCONNECT_PEER
 * to be sent by a Peer to a Client.
 *
 * @author Eleanor McMurtry
 * @author Andrea Law
 */
class DisconnectPeerResponse extends ClientResponse {

    DisconnectPeerResponse(HostPort hostPort) {
        response.append("command", "DISCONNECT_PEER_RESPONSE");

        final String SUCCESS = "disconnected from peer";
        String reply = PeerServer.connection().getPeer(hostPort).matchThen(
                peer -> {
                    PeerServer.connection().closeConnection(peer);
                    return SUCCESS;
                },
                () -> "connection not active"
        );

        response.append("host", hostPort.hostname);
        response.append("port", hostPort.port);
        response.append("status", reply.equals(SUCCESS));
        response.append("message", reply);
    }

}
