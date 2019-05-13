package unimelb.bitbox.messages;

import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.FileSystemManager;

public class FileModifyRequest extends Message {
    public FileModifyRequest(FileSystemManager.FileDescriptor fileDescriptor, String pathName) {
        document.append("command", FILE_MODIFY_REQUEST);

        JsonDocument jsonFileDescriptor = new JsonDocument();
        jsonFileDescriptor.append("md5", fileDescriptor.md5);
        jsonFileDescriptor.append("lastModified", fileDescriptor.lastModified);
        jsonFileDescriptor.append("fileSize", fileDescriptor.fileSize);
        document.append("fileDescriptor", jsonFileDescriptor);

        document.append("pathName", pathName);
    }
}
