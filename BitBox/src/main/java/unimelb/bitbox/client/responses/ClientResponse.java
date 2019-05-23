package unimelb.bitbox.client.responses;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.ResponseFormatException;

/**
 * Parent class of responses to client requests
 */
public abstract class ClientResponse {
    protected JsonDocument response = new JsonDocument();

    protected ClientResponse() {}

    // ELEANOR: In my review I meant that ClientResponse should be a factory, so I've implemented that here
    public static JsonDocument getResponse(String command, ServerMain server, JsonDocument document)
            throws ResponseFormatException {
        switch (command) {
            case "LIST_PEERS_REQUEST":
                return new ListPeersResponse(server).response;
            case "CONNECT_PEER_REQUEST":
                return new ConnectPeerResponse(server, getHostPort(document)).response;
            case "DISCONNECT_PEER_REQUEST":
                return new DisconnectPeerResponse(server, getHostPort(document)).response;
        }
        throw new ResponseFormatException("Unrecognised command `" + command + "`");
    }

    private static HostPort getHostPort(JsonDocument document) throws ResponseFormatException {
        return new HostPort(document.require("host"), document.require("port"));
    }
}
