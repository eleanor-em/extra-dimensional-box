package unimelb.bitbox.messages;

public class DirectoryCreateRequest extends Message {
    private String pathName;

    public DirectoryCreateRequest(String pathName) {
        super("DIRECTORY_CREATE:" + pathName);
        document.append("command", DIRECTORY_CREATE_REQUEST);
        document.append("pathName", pathName);

        this.pathName = pathName;
    }
}
