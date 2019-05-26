package unimelb.bitbox.client.requests;

import org.apache.commons.cli.CommandLine;
import unimelb.bitbox.client.ClientArgsException;
import unimelb.bitbox.util.network.HostPortParseException;

/**
 * Process client commands into messages that can be sent to a Peer.
 */
public class ClientRequestProtocol {
    /**
     * Given a set of command line options, produces the appropriate message to send.
     * @param opts the command line options
     * @return the generated message
     * @throws ClientArgsException in case the options are incorrectly formatted
     */
    public static ClientRequest generateMessage(CommandLine opts)
        throws ClientArgsException {
        String command = opts.getOptionValue("c");
        if (command == null) {
            throw new ClientArgsException("missing command line option: -c");
        }

        try {
            switch (command) {
                case "list_peers":
                    return new ListPeersRequest();
                case "connect_peer":
                    return new ConnectPeerRequest(opts.getOptionValue("p"));
                case "disconnect_peer":
                    return new DisconnectPeerRequest(opts.getOptionValue("p"));
                default:
                    throw new ClientArgsException("invalid command: " + command);
            }
        } catch (HostPortParseException e) {
            throw new ClientArgsException(e.getMessage());
        }
    }
}
