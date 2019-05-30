package unimelb.bitbox.util.concurrency;

import unimelb.bitbox.server.PeerServer;

import java.util.concurrent.*;

// TODO: Allow a reference to the thread to cancel it early
public class KeepAlive {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final CompletionService<ThrowRunnable> completionService = new ExecutorCompletionService<>(executor);

    static {
        executor.submit(KeepAlive::watch);
    }

    public static void submitThrowable(ThrowRunnable task) {
        executor.submit(() -> runAndReturnSelf(task));
    }
    public static void submit(Runnable task) {
        submitThrowable(task::run);
    }

    private static ThrowRunnable runAndReturnSelf(ThrowRunnable task) throws Exception {
        task.run();
        return task;
    }

    private KeepAlive() {}

    private static void watch() {
        while (true) {
            try {
                ThrowRunnable task = completionService.take().get();
                PeerServer.logWarning("resubmitting task " + task);
                submitThrowable(task);
            } catch (InterruptedException | ExecutionException e) {
                PeerServer.logWarning("KeepAlive service threw exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
