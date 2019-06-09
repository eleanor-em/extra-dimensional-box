package unimelb.bitbox.util.fs;

import java.nio.file.FileSystems;

/**
 * Describes the case when a file is
 * created/deleted/modified or when a directory is created/deleted.
 * <li>{@link #name}</li>
 * <li>{@link #path}</li>
 * <li>{@link #pathName}</li>
 * <li>{@link #event}</li>
 * <li>{@link #fileDescriptor}</li>
 */
public class FileSystemEvent {
    /**
     * The path (not including the name) of the file/directory, including
     * the share directory.
     */
    public final String path;
    /**
     * The name (not including any path) of the file/directory.
     */
    public final String name;
    /**
     * The pathname of the file/directory, <i>relative</i> to the share
     * directory.
     */
    public final String pathName;
    /**
     * The type of this event. See {@link FileEventType}.
     */
    public final FileEventType event;
    /**
     * Additional information for the file/directory.
     */
    public final FileDescriptor fileDescriptor;

    /**
     * Constructor for file events.
     *
     * @param path           The path to the file, including the share directory.
     * @param name           The name of the file, excluding its path.
     * @param event          The type of event.
     * @param fileDescriptor The associated file descriptor for the file.
     */
    FileSystemEvent(String path, String name, CharSequence root, FileEventType event, FileDescriptor fileDescriptor) {
        this.path = path;
        this.name = name;
        pathName = (path + FileSystems.getDefault().getSeparator() + name).substring(root.length() + 1);
        this.fileDescriptor = FileDescriptor.rename(fileDescriptor, pathName);
        this.event = event;
    }

    /**
     * Constructor for directory events.
     *
     * @param path  The path to the directory, including the share directory.
     * @param name  The name of the directory.
     * @param event The type of event.
     */
    FileSystemEvent(String path, String name, CharSequence root, FileEventType event) {
        this.path = path;
        this.name = name;
        pathName = (path + FileSystems.getDefault().getSeparator() + name).substring(root.length() + 1);
        fileDescriptor = FileDescriptor.directory(pathName);
        this.event = event;
    }

    public String toString() {
        return event.name() + " " + pathName;
    }
}