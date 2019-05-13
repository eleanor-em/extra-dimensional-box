package unimelb.bitbox.messages;

import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.FileSystemManager;

public class FileCreateRequest extends Message {
    public FileCreateRequest(FileSystemManager.FileDescriptor fileDescriptor, String pathName) {
        document.append("command", FILE_CREATE_REQUEST);

        JsonDocument jsonFileDescriptor = new JsonDocument();
        jsonFileDescriptor.append("md5", fileDescriptor.md5);
        jsonFileDescriptor.append("lastModified", fileDescriptor.lastModified);
        jsonFileDescriptor.append("fileSize", fileDescriptor.fileSize);
        document.append("fileDescriptor", jsonFileDescriptor);

        document.append("pathName", pathName);
    }
}
