package unimelb.bitbox.client.responses;

import unimelb.bitbox.util.JsonDocument;

/**
 * Process messages that can be sent to a Client from a Peer
 * in response to the Client request.
 */
public interface IClientResponseProtocol {

    static JsonDocument getResponse(ClientResponse clientResponse){
        return clientResponse.getResponse();
    }

}