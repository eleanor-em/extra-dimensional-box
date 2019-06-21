package unimelb.bitbox.util.concurrency;

import functional.algebraic.Maybe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an attribute that will eventually be initialised, but may not be immediately.
 * @param <T> the type of the attribute
 */
public class DelayedInitialiser<T> {
    private CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<Maybe<T>> value = new AtomicReference<>(Maybe.nothing());

    /**
     * Waits until the value is set.
     * @return the value
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    public T await() throws InterruptedException {
        latch.await();
        return value.get().get();
    }

    /**
     * Immediately returns a value, or Maybe.nothing() if there is no value.
     * @return the value as a Maybe
     */
    public Maybe<T> get() {
        return value.get();
    }

    /**
     * Sets the value.
     * @param value the value to set to
     */
    public void set(T value) {
        if (this.value.compareAndSet(Maybe.nothing(), Maybe.just(value))) {
            latch.countDown();
        }
    }

    /**
     * Resets the initialiser to its uninitialised state.
     */
    public void reset() {
        value.set(Maybe.nothing());
        latch = new CountDownLatch(1);
    }
}
