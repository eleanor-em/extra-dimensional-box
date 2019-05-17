package unimelb.bitbox.client.requests;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

/**
 * Parent class of requests sent by the Client to a Peer.
 */
abstract public class IClientRequest {
    private final Document document = new Document();

    /**
     * Constructor. Intended for use only by subclasses.
     * @param command the command to send
     */
    protected IClientRequest(String command) {
        document.append("command", command);
    }

    protected IClientRequest(String command, String peerAddress)
            throws IllegalArgumentException {
        document.append("command", command);

        if (peerAddress == null) {
            throw new IllegalArgumentException("missing command line option: -p");
        }
        HostPort hostPort = new HostPort(peerAddress);
        appendHostPort(hostPort);
    }

    /**
     * Adds host and port information, where appropriate.
     * @param hostPort the object to extract the information from
     */
    protected void appendHostPort(HostPort hostPort) {
        document.append("host", hostPort.hostname);
        document.append("port", hostPort.port);
    }

    /**
     * Encodes the message as JSON.
     * @return the encoded message
     */
    public String encoded() {
        return document.toJson();
    }
}
