package unimelb.bitbox.messages;

import unimelb.bitbox.util.network.JSONDocument;

public class FileCreateRequest extends Message {
    public FileCreateRequest(JSONDocument fileDescriptor, String pathName) {
        super("FILE_CREATE:" + pathName + ":" + fileDescriptor.toJson());
        document.append("command", FILE_CREATE_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }
}
