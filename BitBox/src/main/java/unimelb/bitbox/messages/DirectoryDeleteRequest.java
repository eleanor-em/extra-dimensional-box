package unimelb.bitbox.messages;

/**
 * DIRECTORY_DELETE_REQUEST message.
 *
 * @author Eleanor McMurtry
 */
public class DirectoryDeleteRequest extends Message {
    public DirectoryDeleteRequest(String pathName) {
        super("DIRECTORY_DELETE:" + pathName);
        document.append("command", MessageType.DIRECTORY_DELETE_REQUEST);
        document.append("pathName", pathName);
    }
}
