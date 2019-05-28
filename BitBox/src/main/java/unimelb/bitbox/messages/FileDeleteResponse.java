package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.fs.FileSystemManager;

import java.io.IOException;

public class FileDeleteResponse extends Message{
    private static final String SUCCESS = "File deleted";
    public FileDeleteResponse(FileSystemManager fsManager, FileDescriptor fileDescriptor, String pathName, boolean dryRun){
        super("FILE_DELETE:" + pathName + ":" + fileDescriptor);
        if (dryRun) {
            return;
        }
        String reply = SUCCESS;
        try {
            // Try cancelling the file loader first
            if (!fsManager.cancelFileLoader(pathName)) {
                if (!fsManager.isSafePathName(pathName)) {
                    reply = "unsafe pathname given";
                } else if (!fsManager.fileNameExists(pathName)) {
                    reply = "pathname does not exist";
                }
                fsManager.deleteFile(pathName, fileDescriptor.lastModified, fileDescriptor.md5);
            }
        } catch (IOException e) {
            e.printStackTrace();
            reply = "there was a problem deleting the file: " + e.getMessage();
        }
        document.append("command", FILE_DELETE_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
        document.append("message", reply);
        document.append("status", reply == SUCCESS);
    }
}
