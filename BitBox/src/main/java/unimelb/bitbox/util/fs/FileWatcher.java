package unimelb.bitbox.util.fs;


import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class FileWatcher extends Thread {
    private final File file;
    private final Runnable action;
    private final int timeout;

    public FileWatcher(File file, Runnable action, int timeoutMilliseconds) {
        this.file = file;
        this.action = action;
        this.timeout = timeoutMilliseconds;
    }

    @Override
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            Path path = file.toPath().getParent();
            if (path == null) {
                // Take the root path if there was no parent
                path = Paths.get("");
            }
            path.register(watcher, ENTRY_MODIFY);
            while (true) {
                try {
                    WatchKey key = watcher.poll(timeout, TimeUnit.MILLISECONDS);
                    if (key != null) {
                        key.pollEvents().stream()
                                .filter(ev -> ((WatchEvent<Path>)ev).context().toString().equals(file.getName()))
                                .findAny().ifPresent(ignored -> action.run());
                        key.reset();
                    }
                } catch (InterruptedException ignored) {}
                Thread.yield();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
