package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileSystemException;
import unimelb.bitbox.util.fs.FileSystemManager;

public class DirectoryCreateResponse extends Message {
    private static final String SUCCESS = "directory created";

    public DirectoryCreateResponse(FileSystemManager fsManager, String pathName, boolean dryRun) {
        super("DIRECTORY_CREATE:" + pathName);

        if (dryRun) {
            return;
        }
        String reply = SUCCESS;
        if (!fsManager.isSafePathName(pathName)) {
            reply = "unsafe pathname given";
        } else if (fsManager.dirNameExists(pathName)) {
            reply = "pathname already exists";
        } else {
            try {
                fsManager.makeDirectory(pathName);
            } catch (FileSystemException e) {
                e.printStackTrace();
                reply = "there was a problem creating the directory";
            }
        }

        document.append("command", DIRECTORY_CREATE_RESPONSE);
        document.append("pathName", pathName);
        document.append("message", reply);
        document.append("status", reply == SUCCESS);
    }
}
