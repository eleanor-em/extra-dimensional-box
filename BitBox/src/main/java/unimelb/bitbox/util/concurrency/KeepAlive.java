package unimelb.bitbox.util.concurrency;

import unimelb.bitbox.server.PeerServer;

import java.util.concurrent.*;

// TODO: Allow a reference to the thread to cancel it early
public class KeepAlive {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final CompletionService<Runnable> completionService = new ExecutorCompletionService<>(executor);

    static {
        executor.submit(KeepAlive::watch);
    }

    public static void submit(Runnable task) {
        executor.submit(() -> runAndReturnSelf(task));
    }

    private static Runnable runAndReturnSelf(Runnable task) {
        task.run();
        return task;
    }

    private KeepAlive() {}

    private static void watch() {
        while (true) {
            try {
                Runnable task = completionService.take().get();
                PeerServer.logWarning("resubmitting task " + task);
                submit(task);
            } catch (InterruptedException | ExecutionException e) {
                PeerServer.logWarning("KeepAlive service threw exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
