package unimelb.bitbox.peers;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class OutgoingConnection extends Thread {
    private AtomicBoolean active = new AtomicBoolean(true);
    private final BlockingQueue<OutgoingMessage> messages = new LinkedBlockingQueue<>();
    void deactivate() {
        active.set(false);
        interrupt();
    }

    boolean isActive() {
        return !isInterrupted() && active.get();
    }

    protected final void addMessage(OutgoingMessage message) {
        messages.add(message);
    }
    protected OutgoingMessage takeMessage() throws InterruptedException {
        return messages.take();
    }
}