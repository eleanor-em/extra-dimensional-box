package unimelb.bitbox.util.concurrency;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class LazyInitialiser<T> {
    private Supplier<T> supplier;
    private AtomicReference<T> cached = new AtomicReference<>();

    public T get() {
        cached.compareAndSet(null, supplier.get());
        return cached.get();
    }

    public void set(T value) {
        if (value != null) {
            cached.set(value);
        }
    }

    public LazyInitialiser(Supplier<T> supplier) {
        this.supplier = supplier;
    }
}
