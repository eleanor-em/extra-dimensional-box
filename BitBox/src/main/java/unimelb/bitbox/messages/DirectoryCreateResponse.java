package unimelb.bitbox.messages;

import unimelb.bitbox.util.FileSystemManager;

public class DirectoryCreateResponse extends Message {
    private static final String SUCCESS = "directory created";

    public DirectoryCreateResponse(FileSystemManager fsManager, String pathName) {
        String reply = SUCCESS;
        if (!fsManager.isSafePathName(pathName)) {
            reply = "unsafe pathname given";
        } else if (fsManager.dirNameExists(pathName)) {
            reply = "pathname already exists";
        } else if (!fsManager.makeDirectory(pathName)) {
            reply = "there was a problem creating the directory";
        }

        document.append("command", DIRECTORY_CREATE_RESPONSE);
        document.append("pathName", pathName);
        document.append("message", reply);
        document.append("status", reply == SUCCESS);
    }
}
