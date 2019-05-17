package unimelb.bitbox.messages;

import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.ResponseFormatException;


public class FileModifyResponse extends Message {
    private static final String SUCCESS = "file loader ready";
    public final boolean successful;

    public FileModifyResponse(FileSystemManager fsManager, JsonDocument fileDescriptor, String pathName)
            throws ResponseFormatException {
        document.append("command", FILE_MODIFY_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);

        String reply = SUCCESS;
        try {
            String md5 = fileDescriptor.require("md5");
            long lastModified = fileDescriptor.require("lastModified");
            long length = fileDescriptor.require("fileSize");

            if (!fsManager.isSafePathName(pathName)) {
                reply = "unsafe pathname given";
            } else if (fsManager.fileNameExists(pathName, md5)) {
                reply = "file already exists with matching content";
            } else if (!fsManager.fileNameExists(pathName)) {
                reply = "pathname does not exist";
            } else if (!fsManager.modifyFileLoader(pathName, md5, lastModified, length)) {
                reply = "there was a problem modifying the file";
            }
        } catch (ResponseFormatException e){
            throw e;
        } catch (Exception e) {
            reply = "there was a problem modifying the file";
        }

        successful = reply == SUCCESS;
        document.append("message", reply);
        document.append("status", successful);
        document.append("pathName", pathName);
    }
}
