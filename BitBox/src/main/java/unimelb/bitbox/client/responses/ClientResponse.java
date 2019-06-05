package unimelb.bitbox.client.responses;

import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.JSONDocument;

/**
 * Parent class of responses to client requests
 */
public abstract class ClientResponse {
    protected JSONDocument response = new JSONDocument();

    ClientResponse() {}

    public static Result<ServerException, JSONDocument> getResponse(String command, JSONDocument document) {
        switch (command) {
            case "LIST_PEERS_REQUEST":
                return Result.value(new ListPeersResponse().response);
            case "CONNECT_PEER_REQUEST":
                return HostPort.fromJSON(document)
                               .map(addr -> new ConnectPeerResponse(addr).response)
                               .mapError(ServerException::new);
            case "DISCONNECT_PEER_REQUEST":
                return HostPort.fromJSON(document)
                               .map(addr -> new DisconnectPeerResponse(addr).response)
                               .mapError(ServerException::new);
            default:
                return Result.error(new ServerException("Unrecognised command `" + command + "`"));
        }
    }
}
