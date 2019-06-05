package unimelb.bitbox.util.fs;


import unimelb.bitbox.util.functional.algebraic.Maybe;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class FileWatcher extends Thread {
    private final File file;
    private final Runnable action;
    private final int timeout;

    public FileWatcher(File file, Runnable action, int timeoutMilliseconds) {
        this.file = file;
        this.action = action;
        timeout = timeoutMilliseconds;
    }

    @Override
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            // If there was no parent, take the root path
            Path path = Maybe.of(file.toPath().getParent())
                             .fromMaybe(Paths.get(""));
            path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            while (true) {
                try {
                    Maybe.of(watcher.poll(timeout, TimeUnit.MILLISECONDS))
                         .consume(key -> {
                            //noinspection unchecked: trust me, it's safe
                            key.pollEvents().stream()
                                    .filter(ev -> ((WatchEvent<Path>)ev).context().toString().equals(file.getName()))
                                    .findAny().ifPresent(ignored -> action.run());
                            key.reset();
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                Thread.yield();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
