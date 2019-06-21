package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;

/**
 * This class represents a message that has been received but not processed.
 * It stores a raw message as a string, as well as the peer that the message was received from (important for error
 * checking).
 *
 * @author Eleanor McMurtry
 */
public class ReceivedMessage {
    public final String text;
    public final Peer peer;

    public ReceivedMessage(String text, Peer peer) {
        this.text = text;
        this.peer = peer;
    }

    @Override
    public String toString() {
        return peer.getForeignName() + ": " + text;
    }
}
