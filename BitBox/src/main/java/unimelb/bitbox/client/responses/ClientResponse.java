package unimelb.bitbox.client.responses;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;

/**
 * Parent class of responses to client requests
 */
abstract public class ClientResponse {

    ServerMain server;
    JsonDocument response;

    public ClientResponse(ServerMain server, JsonDocument document) {
        this.server = server;
        this.response = document;
    }

    protected JsonDocument getResponse(){
        return response;
    }

}
