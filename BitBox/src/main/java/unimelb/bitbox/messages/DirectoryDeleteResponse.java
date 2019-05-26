package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileSystemManager;

public class DirectoryDeleteResponse extends Message {
    private static final String SUCCESS = "directory deleted";

    public DirectoryDeleteResponse(FileSystemManager fsManager, String pathName, boolean dryRun) {
        super("DIRECTORY_DELETE:" + pathName);

        if (dryRun) {
            return;
        }
        String reply = SUCCESS;
        if (!fsManager.isSafePathName(pathName)) {
            reply = "unsafe pathname given";
        } else if (!fsManager.dirNameExists(pathName)) {
            reply = "pathname does not exist";
        } else if (!fsManager.deleteDirectory(pathName)) {
            reply = "there was a problem deleting the directory";
        }

        document.append("command", DIRECTORY_DELETE_RESPONSE);
        document.append("pathName", pathName);
        document.append("message", reply);
        document.append("status", reply == SUCCESS);
    }
}
