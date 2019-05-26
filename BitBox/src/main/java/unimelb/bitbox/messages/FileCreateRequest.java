package unimelb.bitbox.messages;

import unimelb.bitbox.util.network.JsonDocument;

public class FileCreateRequest extends Message {
    public FileCreateRequest(JsonDocument fileDescriptor, String pathName) {
        super("FILE_CREATE:" + pathName + ":" + fileDescriptor.toJson());
        document.append("command", FILE_CREATE_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }
}
