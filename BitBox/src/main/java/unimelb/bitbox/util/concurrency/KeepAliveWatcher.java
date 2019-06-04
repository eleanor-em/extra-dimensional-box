package unimelb.bitbox.util.concurrency;

import java.util.concurrent.Future;

public class KeepAliveWatcher {
    private Future<?> future;
    private Runnable task;

    KeepAliveWatcher(Future<?> future, Runnable task) {
        this.future = future;
        this.task = task;
    }

    public void cancel() {
        KeepAlive.cancelledTasks.add(task);
        future.cancel(true);
    }
}
