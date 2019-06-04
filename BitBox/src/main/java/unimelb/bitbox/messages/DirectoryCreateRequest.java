package unimelb.bitbox.messages;

public class DirectoryCreateRequest extends Message {

    public DirectoryCreateRequest(String pathName) {
        super("DIRECTORY_CREATE:" + pathName);
        document.append("command", MessageType.DIRECTORY_CREATE_REQUEST);
        document.append("pathName", pathName);
    }
}
