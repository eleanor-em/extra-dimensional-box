package unimelb.bitbox.util.concurrency;

@FunctionalInterface
public interface ThrowRunnable {
    void run() throws Exception;
}
