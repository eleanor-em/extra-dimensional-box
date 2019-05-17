package unimelb.bitbox.client;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

/**
 * Parent class of messages that can be sent by the Client.
 */
abstract public class IClientProtocol {
    private final Document document = new Document();

    /**
     * Constructor. Intended for use only by subclasses.
     * @param command the command to send
     */
    protected IClientProtocol(String command) {
        document.append("command", command);
    }

    protected IClientProtocol(String command, String peerAddress)
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
