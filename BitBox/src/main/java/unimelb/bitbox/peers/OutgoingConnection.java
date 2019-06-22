package unimelb.bitbox.peers;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents an outgoing connection to a peer.
 *
 * @author Eleanor McMurtry
 */
abstract class OutgoingConnection implements Runnable {
    private final BlockingQueue<OutgoingMessage> messages = new LinkedBlockingQueue<>();

    final void addMessage(OutgoingMessage message) {
        messages.add(message);
    }
    final OutgoingMessage takeMessage() throws InterruptedException {
        return messages.take();
    }
}