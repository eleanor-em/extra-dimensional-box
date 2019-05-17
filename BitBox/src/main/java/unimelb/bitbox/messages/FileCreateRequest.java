package unimelb.bitbox.messages;

import unimelb.bitbox.util.JsonDocument;

public class FileCreateRequest extends Message {
    public FileCreateRequest(JsonDocument fileDescriptor, String pathName) {
        document.append("command", FILE_CREATE_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }
}
