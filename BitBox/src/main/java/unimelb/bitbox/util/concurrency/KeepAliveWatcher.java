package unimelb.bitbox.util.concurrency;

import java.util.concurrent.Future;

public class KeepAliveWatcher {
    Future<?> future;
    private Runnable task;

    KeepAliveWatcher(Future<?> future, Runnable task) {
        this.future = future;
        this.task = task;
        KeepAlive.watchers.put(task, this);
    }

    public void cancel() {
        KeepAlive.cancelledTasks.add(task);
        KeepAlive.watchers.remove(task);
        future.cancel(true);
    }
}
