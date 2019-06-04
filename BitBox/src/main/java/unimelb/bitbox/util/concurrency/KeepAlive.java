package unimelb.bitbox.util.concurrency;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.functional.algebraic.Maybe;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class KeepAlive {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final CompletionService<Runnable> completionService = new ExecutorCompletionService<>(executor);

    static final Set<Runnable> cancelledTasks = ConcurrentHashMap.newKeySet();
    static final Map<Runnable, KeepAliveWatcher> watchers = new ConcurrentHashMap<>();

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
            PeerServer.logWarning("KeepAlive service threw exception");
            e.printStackTrace();
        }
        return task;
    }

    private static void watch() {
        while (true) {
            try {
                Runnable task = completionService.take().get();

                if (!cancelledTasks.remove(task)) {
                    PeerServer.logWarning("resubmitting task " + task);
                    Maybe.of(watchers.get(task))
                         .match(watcher -> watcher.future = submitInternal(task),
                                () -> submitInternal(task));
                } else {
                    watchers.remove(task);
                }
            } catch (InterruptedException | ExecutionException e) {
                PeerServer.logWarning("KeepAlive service threw exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static {
        executor.submit(KeepAlive::watch);
    }
    private KeepAlive() {}
}


