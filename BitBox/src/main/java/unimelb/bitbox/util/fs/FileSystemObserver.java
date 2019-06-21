package unimelb.bitbox.util.fs;

/**
 * Observes file system events from a FileSystemManager.
 *
 * @author Aaron Harwood
 */
@FunctionalInterface
public interface FileSystemObserver {
	void processFileSystemEvent(FileSystemEvent fileSystemEvent);
}
