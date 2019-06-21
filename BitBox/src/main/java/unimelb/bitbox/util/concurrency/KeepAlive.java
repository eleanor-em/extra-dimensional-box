package unimelb.bitbox.util.concurrency;

import functional.algebraic.Maybe;
import unimelb.bitbox.server.PeerServer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * A service that continually runs a task, restarting it if it fails.
 *
 * @author Eleanor McMurtry
 */
public class KeepAlive {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final CompletionService<Runnable> completionService = new ExecutorCompletionService<>(executor);

    static final Set<Runnable> cancelledTasks = ConcurrentHashMap.newKeySet();
    static final Map<Runnable, KeepAliveWatcher> watchers = new ConcurrentHashMap<>();

    /**
     * Submits a task.
     * @param task the task to submit
     * @return an object that can be used to cancel the task
     */
    public static KeepAliveWatcher submit(Runnable task) {
        return new KeepAliveWatcher(submitInternal(task), task);
    }

    private static Future<?> submitInternal(Runnable task) {
        return executor.submit(() -> runAndReturnSelf(task));
    }

    private static Runnable runAndReturnSelf(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            PeerServer.log().warning("KeepAlive service threw exception: " + e.getMessage());
            e.printStackTrace();
        }
        return task;
    }

    private static void watch() {
        while (true) {
            try {
                Runnable task = completionService.take().get();
                PeerServer.log().warning("task " + task + " terminated");

                if (cancelledTasks.remove(task)) {
                    watchers.remove(task);
                } else {
                    // TODO: pretty sure this doesn't work -- EM
                    PeerServer.log().warning("resubmitting task " + task);
                    Maybe.of(watchers.get(task))
                            .match(watcher -> watcher.future = submitInternal(task),
                                    () -> submitInternal(task));
                }
            } catch (InterruptedException | ExecutionException e) {
                PeerServer.log().warning("KeepAlive service threw exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static {
        executor.submit(KeepAlive::watch);
    }
    private KeepAlive() {}
}


