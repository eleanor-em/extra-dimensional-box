package unimelb.bitbox.client.requests;

import org.apache.commons.cli.CommandLine;
import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.JSONDocument;

/**
 * Process client commands into messages that can be sent to a Peer.
 */
public class ClientRequestProtocol {
    /**
     * Given a set of command line options, produces the appropriate message to send.
     * @param opts the command line options
     * @return the generated message
     */
    public static Result<ClientArgsException, JSONDocument> generateMessage(CommandLine opts) {
        String command = opts.getOptionValue("c");
        if (command == null) {
             return Result.error(new ClientArgsException("missing command line option: -c"));
        }

        try {
            switch (command) {
                case "list_peers":
                    return Result.value(new ListPeersRequest().toJSON());
                case "connect_peer":
                    return Result.value(new ConnectPeerRequest(opts.getOptionValue("p")).toJSON());
                case "disconnect_peer":
                    return Result.value(new DisconnectPeerRequest(opts.getOptionValue("p")).toJSON());
                default:
                    return Result.error(new ClientArgsException("invalid command: " + command));
            }
        } catch (ClientArgsException e) {
            return Result.error(e);
        }
    }
}
