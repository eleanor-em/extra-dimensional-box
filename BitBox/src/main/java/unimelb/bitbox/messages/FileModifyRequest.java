package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileDescriptor;

/**
 * FILE_MODIFY_REQUEST message.
 *
 * @author Eleanor McMurtry
 */
public class FileModifyRequest extends Message {
    public FileModifyRequest(FileDescriptor fd) {
        super("MODIFY:" + fd);
        document.append("command", MessageType.FILE_MODIFY_REQUEST);
        document.append("fileDescriptor", fd);
        document.append("pathName", fd.pathName);
    }
}
