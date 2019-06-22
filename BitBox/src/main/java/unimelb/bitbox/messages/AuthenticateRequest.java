package unimelb.bitbox.messages;

import functional.combinator.Combinators;
import unimelb.bitbox.peers.Peer;

import javax.crypto.SecretKey;
import java.util.Base64;

public class AuthenticateRequest extends Message {
    public AuthenticateRequest(Peer peer, SecretKey solution) {
        super("AUTHENTICATE");

        document.append("command", MessageType.AUTHENTICATE_REQUEST);
        document.append("solution", Base64.getEncoder().encodeToString(solution.getEncoded()));
        peer.getChallenge().match(challenge -> document.append("challenge", challenge), Combinators::noop);
    }
}
