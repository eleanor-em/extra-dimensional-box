package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;

/**
 * Base class for all responses.
 *
 * @author Eleanor McMurtry
 */
public abstract class Response extends Message {
    final Peer peer;

    Response(String summary, Peer peer) {
        super(summary);

        this.peer = peer;
    }

    /**
     * A method that is called when the message is encoded; typically performs I/O.
     */
    abstract void onSent();

    @Override
    public final String networkEncode() {
        onSent();
        return super.networkEncode();
    }
}
