package unimelb.bitbox.messages;

import unimelb.bitbox.util.Document;

public class DirectoryCreateRequest extends Message {
    public DirectoryCreateRequest(String pathName) {
        document.append("command", DIRECTORY_CREATE_REQUEST);
        document.append("pathName", pathName);
    }
}
