package unimelb.bitbox.client.responses;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

/**
 * Parent class of responses to client requests
 */
public abstract class ClientResponse {
    protected JSONDocument response = new JSONDocument();

    protected ClientResponse() {}

    public static Result<ServerException, JSONDocument> getResponse(String command, PeerServer server, JSONDocument document) {
        Result<JSONException, JSONDocument> result;
        switch (command) {
            case "LIST_PEERS_REQUEST":
                result = Result.value(new ListPeersResponse(server).response);
                break;
            case "CONNECT_PEER_REQUEST":
                result = Result.of(() -> new ConnectPeerResponse(server, HostPort.fromJSON(document).get()).response);
                break;
            case "DISCONNECT_PEER_REQUEST":
                result = Result.of(() -> new DisconnectPeerResponse(server, HostPort.fromJSON(document).get()).response);
                break;
            default:
                return Result.error(new ServerException("Unrecognised command `" + command + "`"));
        }
        return result.mapError(ServerException::new);
    }
}
