package unimelb.bitbox.util.fs;

import unimelb.bitbox.util.fs.FileSystemManager.FileSystemEvent;

public interface FileSystemObserver {
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent);
}
