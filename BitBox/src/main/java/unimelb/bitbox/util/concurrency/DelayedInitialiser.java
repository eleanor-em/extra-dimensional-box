package unimelb.bitbox.util.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DelayedInitialiser<T> {
    private CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<T> value = new AtomicReference<>();

    public T await() throws InterruptedException {
        latch.await();
        return value.get();
    }

    public T get() {
        return value.get();
    }

    public void set(T value) {
        if (this.value.compareAndSet(null, value)) {
            latch.countDown();
        }
    }

    public void reset() {
        value.set(null);
        latch = new CountDownLatch(1);
    }
}
