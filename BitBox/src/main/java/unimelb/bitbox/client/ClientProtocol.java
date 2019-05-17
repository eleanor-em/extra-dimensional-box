package unimelb.bitbox.client;

import org.apache.commons.cli.CommandLine;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

/**
 * A message that can be sent by the Client.
 */
public class ClientProtocol extends IClientProtocol{
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

    /**
     * Constructor. Intended for use only by subclasses.
     * @param command the command to send
     */
//    protected ClientRequestProtocol(String command) {
//        document.append("command", command);
//    }
    protected ClientProtocol(String command) {
        super(command);
    }

    protected ClientProtocol(String command, String peerAddress)
            throws IllegalArgumentException {
        super(command, peerAddress);
    }

//
//    protected ClientProtocol(String command, String peerAddress)
//            throws IllegalArgumentException {
//        super(command);
//
//        if (peerAddress == null) {
//            throw new IllegalArgumentException("missing command line option: -p");
//        }
//        HostPort hostPort = new HostPort(peerAddress);
//        appendHostPort(hostPort);
//    }

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
//class PeerProtocol extends ClientRequestProtocol {
//    public PeerProtocol(String command, String peerAddress)
//            throws IllegalArgumentException {
//        super(command);
//
//        if (peerAddress == null) {
//            throw new IllegalArgumentException("missing command line option: -p");
//        }
//        HostPort hostPort = new HostPort(peerAddress);
//        appendHostPort(hostPort);
//    }
//}
//
//class ConnectPeerRequest extends PeerProtocol {
//    public ConnectPeerRequest(String peerAddress) throws IllegalArgumentException {
//        super("CONNECT_PEER_REQUEST", peerAddress);
//    }
//}
//
//class DisconnectPeerRequest extends PeerProtocol {
//    public DisconnectPeerRequest(String peerAddress) throws IllegalArgumentException {
//        super("DISCONNECT_PEER_REQUEST", peerAddress);
//    }
//}
