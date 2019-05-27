package unimelb.bitbox.messages;

import unimelb.bitbox.util.network.JSONDocument;

public class FileModifyRequest extends Message {
    public FileModifyRequest(JSONDocument fileDescriptor, String pathName) {
        super("MODIFY:" + pathName + ":" + fileDescriptor);
        document.append("command", FILE_MODIFY_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }
}
