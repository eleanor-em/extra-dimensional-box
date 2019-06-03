package unimelb.bitbox.peers;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class OutgoingConnection implements Runnable {
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final BlockingQueue<OutgoingMessage> messages = new LinkedBlockingQueue<>();

    final boolean isActive() {
        return active.get();
    }

    final void addMessage(OutgoingMessage message) {
        messages.add(message);
    }
    final OutgoingMessage takeMessage() throws InterruptedException {
        return messages.take();
    }
}