package unimelb.bitbox.client.requests;

import functional.algebraic.Maybe;
import functional.algebraic.Result;
import org.apache.commons.cli.CommandLine;
import unimelb.bitbox.util.network.JSONDocument;

/**
 * Process client commands into messages that can be sent to a Peer.
 */
public class ClientRequestProtocol {
    private ClientRequestProtocol() {}

    /**
     * Given a set of command line options, produces the appropriate message to send.
     * @param opts the command line options
     * @return the generated message, or an error if there was one
     */
    public static Result<JSONDocument, ClientArgsException> generateMessage(CommandLine opts) {
        Maybe<String> maybeCommand = Maybe.of(opts.getOptionValue("c"));
        if (!maybeCommand.isJust()) {
             return Result.error(new ClientArgsException("missing command line option: -c"));
        }
        String command = maybeCommand.get();

        try {
            // Store the peer address or an error for later
            Result<String, ClientArgsException> peerAddress =
                    Maybe.of(opts.getOptionValue("p")).isJust()
                    ? Result.value(opts.getOptionValue("p"))
                    : Result.error(new ClientArgsException("missing command line option: -p"));

            switch (command) {
                case "list_peers":
                    return Result.value(new ListPeersRequest().toJSON());
                case "connect_peer":
                    return Result.value(new ConnectPeerRequest(peerAddress.get()).toJSON());
                case "disconnect_peer":
                    return Result.value(new DisconnectPeerRequest(peerAddress.get()).toJSON());
                default:
                    return Result.error(new ClientArgsException("invalid command: " + command));
            }
        } catch (ClientArgsException e) {
            return Result.error(e);
        }
    }
}
