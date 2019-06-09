package unimelb.bitbox.util.fs;

@FunctionalInterface
public interface FileSystemObserver {
	void processFileSystemEvent(FileSystemEvent fileSystemEvent);
}
