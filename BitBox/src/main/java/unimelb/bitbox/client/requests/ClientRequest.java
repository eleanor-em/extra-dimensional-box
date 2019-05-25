package unimelb.bitbox.client.requests;

import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.HostPortParseException;

/**
 * Parent class of requests sent by the Client to a Peer.
 */
abstract public class ClientRequest {
    private final JsonDocument document = new JsonDocument();

    /**
     * Constructor. Intended for use only by subclasses.
     * @param command the command to send
     */
    protected ClientRequest(String command) {
        document.append("command", command);
    }

    protected ClientRequest(String command, String peerAddress)
            throws HostPortParseException {
        document.append("command", command);

        if (peerAddress == null) {
            throw new IllegalArgumentException("missing command line option: -p");
        }
        HostPort hostPort = HostPort.fromAddress(peerAddress);
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
     * Returns the generated JsonDocument.
     * @return the document
     */
    public JsonDocument getDocument() {
        return document;
    }
}
