package unimelb.bitbox.util.concurrency;

import functional.algebraic.Maybe;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Represents an attribute that can be initialised any time, but isn't on creation.
 * @param <T> the type of the attribute
 */
public class LazyInitialiser<T> {
    private final Supplier<? extends T> supplier;
    private final AtomicReference<Maybe<T>> cached = new AtomicReference<>(Maybe.nothing());

    /**
     * Gets the value, initialising it if it isn't already.
     * @return the contained value
     */
    public T get() {
        cached.compareAndSet(Maybe.nothing(), Maybe.just(supplier.get()));
        return cached.get().get();
    }

    /**
     * Sets the value of the attribute, discarding the initialiser if it's not initialised.
     * @param value the value to set to
     */
    public void set(T value) {
        cached.set(Maybe.just(value));
    }

    /**
     * Create a lazy initialiser.
     * @param supplier a function that can be called any time to provide the initial value
     */
    public LazyInitialiser(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }
}
