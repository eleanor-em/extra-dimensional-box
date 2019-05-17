package unimelb.bitbox.client;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;

/**
 * Parent class of peer responses to client requests
 */
abstract public class IPeerResponse {
    ServerMain server;
    JsonDocument response;

    public IPeerResponse(ServerMain server, JsonDocument response) {
        this.server = server;
        this.response = response;
    }

    protected JsonDocument getResponse(){
        return response;
    }
}
