package unimelb.bitbox.client.responses;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.network.HostPort;

/**
 * Generates the message content of CONNECT_PEER
 * to be sent by a Peer to a Client.
 *
 * @author Eleanor McMurtry
 * @author Andrea Law
 */
class ConnectPeerResponse extends ClientResponse {

    ConnectPeerResponse(HostPort hostPort) {
        response.append("command", "CONNECT_PEER_RESPONSE");

        final String SUCCESS = "connected to peer";
        String reply = SUCCESS;

        if (!PeerServer.connection().clientTryPeer(hostPort)){
            PeerServer.log().warning("failed to connect to " + hostPort);
            reply = "connection failed";
        }

        response.append("host", hostPort.hostname);
        response.append("port", hostPort.port);
        response.append("status", reply.equals(SUCCESS));
        response.append("message", reply);
    }

}
