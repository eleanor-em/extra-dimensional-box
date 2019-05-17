package unimelb.bitbox.messages;

import unimelb.bitbox.util.JsonDocument;

public class FileModifyRequest extends Message {
    public FileModifyRequest(JsonDocument fileDescriptor, String pathName) {
        super("MODIFY:" + pathName + ":" + fileDescriptor.toJson());
        document.append("command", FILE_MODIFY_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }
}
