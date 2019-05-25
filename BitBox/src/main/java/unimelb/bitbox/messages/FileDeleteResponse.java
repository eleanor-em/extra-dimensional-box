package unimelb.bitbox.messages;

import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.FileSystemManager;

public class FileDeleteResponse extends Message{
    private static final String SUCCESS = "File deleted";
    public FileDeleteResponse(FileSystemManager fsManager, JsonDocument fileDescriptor, String pathName, boolean dryRun){
        super("FILE_DELETE:" + pathName + ":" + fileDescriptor.toJson());
        if (dryRun) {
            return;
        }
        String reply = SUCCESS;
        try {
            if (!fsManager.isSafePathName(pathName)) {
                reply = "unsafe pathname given";
            } else if (!fsManager.fileNameExists(pathName)) {
                reply = "pathname does not exist";
            } else {
                long lastModified = fileDescriptor.require("lastModified");
                String md5 = fileDescriptor.require("md5");
                if (!fsManager.deleteFile(pathName, lastModified, md5)) {
                    reply = "there was a problem deleting the file";
                }
            }
        } catch (Exception e) {
            reply = "there was a problem deleting the file";
        }
        document.append("command", FILE_DELETE_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
        document.append("message", reply);
        document.append("status", reply == SUCCESS);
    }
}
