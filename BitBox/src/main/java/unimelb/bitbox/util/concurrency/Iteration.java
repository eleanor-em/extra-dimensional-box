package unimelb.bitbox.util.concurrency;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.function.Consumer;

public class Iteration {
    /**
     * Attempt to iterate over the collection. If concurrent modification occurs, we abandon the attempt.
     * @param c     the collection to iterate over
     * @param func  the function to apply
     * @return whether the iteration succeeded
     */
    @SuppressWarnings("ProhibitedExceptionCaught")
    public static <T> boolean forEachAsync(Collection<T> c, Consumer<? super T> func) {
        try {
            c.forEach(func);
            return true;
        } catch (ConcurrentModificationException ignored) {
            return false;
        }
    }

    private Iteration() {}
}
