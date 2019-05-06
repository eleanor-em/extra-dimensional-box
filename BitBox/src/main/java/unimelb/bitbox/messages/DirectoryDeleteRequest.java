package unimelb.bitbox.messages;

public class DirectoryDeleteRequest extends Message {
    public DirectoryDeleteRequest(String pathName) {
        document.append("command", DIRECTORY_DELETE_REQUEST);
        document.append("pathName", pathName);
    }
}
