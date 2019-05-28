package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.fs.FileSystemManager;

import java.io.IOException;


public class FileModifyResponse extends Message {
    private static final String SUCCESS = "file loader ready";
    public final boolean successful;

    public FileModifyResponse(FileSystemManager fsManager, FileDescriptor fileDescriptor, String pathName, boolean dryRun) {
        super("MODIFY:" + pathName + ":" + fileDescriptor);
        if (dryRun) {
            successful = false;
            return;
        }
        document.append("command", FILE_MODIFY_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);

        String reply = SUCCESS;
        try {
            if (!fsManager.isSafePathName(pathName)) {
                reply = "unsafe pathname given";
            } else if (fsManager.fileNameExists(pathName, fileDescriptor.md5)) {
                reply = "file already exists with matching content";
            } else if (!fsManager.fileNameExists(pathName)) {
                reply = "pathname does not exist";
            } else {
                fsManager.modifyFileLoader(pathName, fileDescriptor);
            }
        } catch (IOException e) {
            e.printStackTrace();
            reply = "there was a problem modifying the file";
        }

        successful = reply == SUCCESS;
        document.append("message", reply);
        document.append("status", successful);
        document.append("pathName", pathName);
    }
}
