package unimelb.bitbox.util.concurrency;

import unimelb.bitbox.util.functional.algebraic.Maybe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DelayedInitialiser<T> {
    private CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<Maybe<T>> value = new AtomicReference<>(Maybe.nothing());

    public T await() throws InterruptedException {
        latch.await();
        return value.get().get();
    }

    public Maybe<T> get() {
        return value.get();
    }

    public void set(T value) {
        if (this.value.compareAndSet(Maybe.nothing(), Maybe.just(value))) {
            latch.countDown();
        }
    }

    public void reset() {
        value.set(Maybe.nothing());
        latch = new CountDownLatch(1);
    }
}
