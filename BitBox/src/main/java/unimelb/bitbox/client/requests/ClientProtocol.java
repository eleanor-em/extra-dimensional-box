package unimelb.bitbox.client.requests;

import org.apache.commons.cli.CommandLine;

/**
 * Process client commands into messages that can be sent to a Peer.
 */
public class ClientProtocol {
    /**
     * Given a set of command line options, produces the appropriate message to send.
     * @param opts the command line options
     * @return the generated message
     * @throws IllegalArgumentException in case the options are incorrectly formatted
     */
    public static IClientRequest generateMessage(CommandLine opts)
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
