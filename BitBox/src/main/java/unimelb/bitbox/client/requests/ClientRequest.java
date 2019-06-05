package unimelb.bitbox.client.requests;

import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.IJSONData;
import unimelb.bitbox.util.network.JSONDocument;

/**
 * Parent class of requests sent by the Client to a Peer.
 */
public abstract class ClientRequest implements IJSONData {
    private final JSONDocument document = new JSONDocument();

    /**
     * Construct a request that has only a command
     */
    ClientRequest(String command) {
        document.append("command", command);
    }

    /**
     * Construct a request that has a command and a target peer.
     * @throws ClientArgsException if the address is malformed
     */
    ClientRequest(String command, String peerAddress)
            throws ClientArgsException {
        this(command);

        HostPort hostPort = HostPort.fromAddress(peerAddress)
                .mapError(ClientArgsException::new)
                .get();
        appendHostPort(hostPort);
    }

    /**
     * Adds host and port information, where appropriate.
     * @param hostPort the object to extract the information from
     */
    private void appendHostPort(HostPort hostPort) {
        document.append("host", hostPort.hostname);
        document.append("port", hostPort.port);
    }

    /**
     * Returns the generated JSONDocument.
     * @return the document
     */
    @Override
    public JSONDocument toJSON() {
        return document;
    }
}
