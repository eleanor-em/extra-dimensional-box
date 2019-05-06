package unimelb.bitbox.messages;

import unimelb.bitbox.PeerConnection;

/*
 * This class represents a message that has been received but not processed.
 * It stores a raw message as a string, as well as the peer that the message was received from (important for error
 * checking).
 */
public class ReceivedMessage {
    public final String text;
    public final PeerConnection peer;

    public ReceivedMessage(String text, PeerConnection peer) {
        this.text = text;
        this.peer = peer;
    }

    @Override
    public String toString() {
        return peer.name + ": " + text;
    }
}
