package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;

import java.util.Base64;

public class AuthenticateResponse extends Response {
    private final String challenge;
    private static final String SUCCESS = "decrypted key";

    public AuthenticateResponse(Peer peer, String challenge) {
        super("AUTHENTICATE", peer);

        document.append("command", MessageType.AUTHENTICATE_RESPONSE);
        this.challenge = challenge;
    }

    @Override
    void onSent() {
        String message = PeerServer.groupManager().solveChallenge(challenge)
                                 .matchThen(
                                         solution -> {
                                             document.append("solution", Base64.getEncoder().encodeToString(solution.getEncoded()));
                                             return SUCCESS;
                                         }, e -> "failed to decrypt key"
                                 );
        document.append("status", message.equals(SUCCESS));
        document.append("message", message);
    }
}
