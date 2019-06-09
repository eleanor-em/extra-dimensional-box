package unimelb.bitbox.util.fs;

/**
 * Possible file system events.
 * <li>{@link #FILE_CREATE}</li>
 * <li>{@link #FILE_DELETE}</li>
 * <li>{@link #FILE_MODIFY}</li>
 * <li>{@link #DIRECTORY_CREATE}</li>
 * <li>{@link #DIRECTORY_DELETE}</li>
 */
public enum FileEventType {
    /**
     * A new file has been created. The parent directory must
     * exist for this event to be emitted.
     */
    FILE_CREATE,
    /**
     * An existing file has been deleted.
     */
    FILE_DELETE,
    /**
     * An existing file has been modified.
     */
    FILE_MODIFY,
    /**
     * A new directory has been created. The parent directory must
     * exist for this event to be emitted.
     */
    DIRECTORY_CREATE,
    /**
     * An existing directory has been deleted. The directory must
     * be empty for this event to be emitted, and its parent
     * directory must exist.
     */
    DIRECTORY_DELETE
}
