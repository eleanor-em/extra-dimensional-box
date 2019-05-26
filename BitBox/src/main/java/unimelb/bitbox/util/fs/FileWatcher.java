package unimelb.bitbox.util.fs;


import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class FileWatcher extends Thread {
    private final File file;
    private final Runnable action;
    private final int timeout;

    public FileWatcher(File file, Runnable action) {
        this(file, action, 1000);
    }
    public FileWatcher(File file, Runnable action, int timeoutMilliseconds) {
        this.file = file;
        this.action = action;
        this.timeout = timeoutMilliseconds;
    }

    private static <R> Stream<R> castFilter(Object obj) {
        try {
            return Stream.of((R)obj);
        } catch (ClassCastException e) {
            return Stream.empty();
        }
    }

    @Override
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            System.out.println(file.toPath().toString());
            Path path = file.toPath().getParent();
            if (path == null) {
                // Take the root path if there was no parent
                path = Paths.get("");
            }
            path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            while (true) {
                try {
                    WatchKey key = watcher.poll(timeout, TimeUnit.MILLISECONDS);
                    if (key != null) {
                        key.pollEvents().stream()
                                .flatMap(FileWatcher::<WatchEvent<Path>>castFilter)
                                .filter(ev -> ev.kind() == StandardWatchEventKinds.ENTRY_MODIFY)
                                .filter(ev -> ev.context().toString().equals(file.getName()))
                                .findFirst().ifPresent(ignored -> action.run());
                    }
                } catch (InterruptedException ignored) {}
                Thread.yield();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
