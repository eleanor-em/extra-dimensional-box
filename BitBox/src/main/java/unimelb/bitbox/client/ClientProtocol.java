package unimelb.bitbox.client;

import org.apache.commons.cli.CommandLine;
import unimelb.bitbox.util.Document;

/**
 * A message that can be sent by the Client.
 */
public class ClientProtocol {
    private final Document document = new Document();

    /**
     * Given a set of command line options, produces the appropriate message to send.
     * @param opts the command line options
     * @return the generated message
     * @throws IllegalArgumentException in case the options are incorrectly formatted
     */
    public static IClientProtocol generateMessage(CommandLine opts)
        throws IllegalArgumentException {
        String command = opts.getOptionValue("c");
        if (command == null) {
            throw new IllegalArgumentException("missing command line option: -c");
        }

        switch (command) {
            case "list_peers":
                return new ListPeersRequest();
            case "connect_peer":
                return new ConnectPeerRequest(opts.getOptionValue("p"));
            case "disconnect_peer":
                return new DisconnectPeerRequest(opts.getOptionValue("p"));
            default:
                throw new IllegalArgumentException("invalid command: " + command);
        }
    }
}

class ListPeersRequest extends IClientProtocol {
    ListPeersRequest() {
        super("LIST_PEERS_REQUEST");
    }
}


class ConnectPeerRequest extends IClientProtocol {
    public ConnectPeerRequest(String peerAddress) throws IllegalArgumentException {
        super("CONNECT_PEER_REQUEST", peerAddress);
    }
}

class DisconnectPeerRequest extends IClientProtocol {
    public DisconnectPeerRequest(String peerAddress) throws IllegalArgumentException {
        super("DISCONNECT_PEER_REQUEST", peerAddress);
    }
}
