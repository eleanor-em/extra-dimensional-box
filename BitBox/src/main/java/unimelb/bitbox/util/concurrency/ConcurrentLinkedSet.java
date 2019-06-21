package unimelb.bitbox.util.concurrency;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * A thread-safe Set that also maintains insertion order, and allows the first element to be retrieved.
 * Only has the methods I actually need, to save time.
 */
public class ConcurrentLinkedSet<E> {
    private final Set<E> set = new LinkedHashSet<>();

    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    /**
     * Adds an item to the set.
     * @param item the item to add
     * @return whether the item was successfully added (i.e. false if the item was already present)
     */
    public boolean add(E item) {
        lock.lock();
        boolean result = set.add(item);

        if (result) {
            notEmpty.signal();
        } else {
            lock.unlock();
        }
        return result;
    }

    /**
     * Removes an item from the set.
     * @param item the item to remove
     * @return whether the item was present
     */
    public boolean remove(E item) {
        try {
            lock.lock();
            return set.remove(item);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all elements that satisfy a predicate.
     * @param pred the predicate to use
     * @return whether any elements were removed
     */
    public boolean removeIf(Predicate<E> pred) {
        try {
            lock.lock();
            return set.removeIf(pred);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the first item of the set. Blocks until the set is non-empty.
     * @return the first item of the set
     * @throws InterruptedException if the thread was interrupted while blocked
     */
    public E take() throws InterruptedException {
        try {
            lock.lock();
            while (set.isEmpty()) {
                notEmpty.await();
            }
            int size = set.size();

            Iterator<E> it = set.iterator();
            E value = it.next();
            it.remove();

            assert set.size() < size;
            return value;
        } finally {
            lock.unlock();
        }
    }

}
