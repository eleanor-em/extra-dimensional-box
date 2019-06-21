package unimelb.bitbox.util.fs;


import functional.algebraic.Maybe;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * A thread that watches a file for changes, and runs an action whenever a change occurs.
 *
 * @author Eleanor McMurtry
 */
public class FileWatcher extends Thread {
    private final File file;
    private final Runnable action;
    private final int timeout;

    /**
     * Create the thread.
     * @param file the file to watch
     * @param action the action to perform
     * @param timeoutMilliseconds how long each poll should wait
     */
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
                             .orElse(Paths.get(""));
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
