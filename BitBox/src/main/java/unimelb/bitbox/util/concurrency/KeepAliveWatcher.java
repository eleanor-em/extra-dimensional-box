package unimelb.bitbox.util.concurrency;

import java.util.concurrent.Future;

/**
 * A class that watches a {@link KeepAlive} task.
 *
 * @author Eleanor McMurtry
 */
public class KeepAliveWatcher {
    Future<?> future;
    private final Runnable task;

    KeepAliveWatcher(Future<?> future, Runnable task) {
        this.future = future;
        this.task = task;
        KeepAlive.watchers.put(task, this);
    }

    /**
     * Cancels the task.
     */
    public void cancel() {
        KeepAlive.cancelledTasks.add(task);
        KeepAlive.watchers.remove(task);
        future.cancel(true);
    }
}
