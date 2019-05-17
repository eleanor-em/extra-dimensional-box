package unimelb.bitbox.client.responses;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;

/**
 * Parent class of responses to client requests
 */
abstract public class IClientResponse {
    ServerMain server;
    JsonDocument response;

    public IClientResponse(ServerMain server, JsonDocument response) {
        this.server = server;
        this.response = response;
    }

    public JsonDocument getResponse(){
        return response;
    }
}
