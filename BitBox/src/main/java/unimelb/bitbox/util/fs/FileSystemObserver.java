package unimelb.bitbox.util.fs;

import unimelb.bitbox.util.fs.FileSystemManager.FileSystemEvent;

@FunctionalInterface
public interface FileSystemObserver {
	void processFileSystemEvent(FileSystemEvent fileSystemEvent);
}
