package unimelb.bitbox.client.responses;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.network.HostPort;

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

    protected ConnectPeerResponse(HostPort hostPort) {
        response.append("command", "CONNECT_PEER_RESPONSE");

        final String SUCCESS = "connected to peer";
        String reply = SUCCESS;

        if (!PeerServer.getConnection().clientTryPeer(hostPort)){
            PeerServer.logWarning("target peer is not reachable. Failed to connect to " + hostPort);
            reply = "connection failed";
        }

        response.append("host", hostPort.hostname);
        response.append("port", hostPort.port);
        response.append("status", reply.equals(SUCCESS));
        response.append("message", reply);
    }

}
