package unimelb.bitbox.util;

import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public interface FileSystemObserver {
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent);
}
