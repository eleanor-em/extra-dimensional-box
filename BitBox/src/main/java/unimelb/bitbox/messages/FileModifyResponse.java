package unimelb.bitbox.messages;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;


public class FileModifyResponse extends Message {
    private static final String SUCCESS = "file loader ready";
    public final boolean successful;

    public FileModifyResponse(FileSystemManager fsManager, Document json, String pathName) {
        document.append("command", FILE_MODIFY_RESPONSE);
        Document fileDescriptor = (Document) json.get("fileDescriptor");

        document.append("fileDescriptor", fileDescriptor);

        String reply = SUCCESS;
        try {
            String md5 = fileDescriptor.getString("md5");
            long lastModified = fileDescriptor.getLong("lastModified");
            long length = fileDescriptor.getLong("fileSize");

            if (!fsManager.isSafePathName(pathName)) {
                reply = "unsafe pathname given";
            } else if (fsManager.fileNameExists(pathName, md5)) {
                reply = "file already exists with matching content";
            } else if (!fsManager.fileNameExists(pathName)) {
                reply = "pathname does not exist";
            } else if (!fsManager.modifyFileLoader(pathName, md5, lastModified, length)) {
                reply = "there was a problem modifying the file";
            }
        } catch (Exception e) {
            reply = "there was a problem modifying the file";
        }

        successful = reply == SUCCESS;
        document.append("message", reply);
        document.append("status", successful);
        document.append("pathName", pathName);
    }
}
