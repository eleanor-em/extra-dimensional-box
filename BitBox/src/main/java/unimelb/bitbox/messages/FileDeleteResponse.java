package unimelb.bitbox.messages;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

public class FileDeleteResponse extends Message{
    private static final String SUCCESS = "File deleted";
    public FileDeleteResponse(FileSystemManager fsManager, Document json, String pathName){
        String reply = SUCCESS;
        Document fileDescriptor = (Document) json.get("fileDescriptor");
        try {
            if (!fsManager.isSafePathName(pathName)) {
                reply = "unsafe pathname given";
            } else if (!fsManager.fileNameExists(pathName)) {
                reply = "pathname does not exists";
            } else {
                long lastModified = fileDescriptor.getLong("lastModified");
                String md5 = fileDescriptor.getString("md5");
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
