package unimelb.bitbox.util.concurrency;

import unimelb.bitbox.server.PeerServer;

import java.util.Set;
import java.util.concurrent.*;

public class KeepAlive {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final CompletionService<Runnable> completionService = new ExecutorCompletionService<>(executor);

    static final Set<Runnable> cancelledTasks = ConcurrentHashMap.newKeySet();

    public static KeepAliveWatcher submit(Runnable task) {
        return new KeepAliveWatcher(executor.submit(() -> runAndReturnSelf(task)), task);
    }

    private static Runnable runAndReturnSelf(Runnable task) {
        task.run();
        return task;
    }

    private static void watch() {
        while (true) {
            try {
                Runnable task = completionService.take().get();

                if (!cancelledTasks.remove(task)) {
                    PeerServer.logWarning("resubmitting task " + task);
                    submit(task);
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


