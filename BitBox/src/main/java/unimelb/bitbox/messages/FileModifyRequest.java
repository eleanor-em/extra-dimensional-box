package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileDescriptor;

/**
 * FILE_MODIFY_REQUEST message.
 *
 * @author Eleanor McMurtry
 */
public class FileModifyRequest extends Message {
    public FileModifyRequest(FileDescriptor fileDescriptor) {
        super("MODIFY:" + fileDescriptor);
        document.append("command", MessageType.FILE_MODIFY_REQUEST);
        document.join(fileDescriptor.toJSON());
    }
}
