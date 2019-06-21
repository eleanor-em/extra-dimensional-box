package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileDescriptor;

/**
 * FILE_CREATE_REQUEST message.
 *
 * @author Eleanor McMurtry
 */
public class FileCreateRequest extends Message {
    public FileCreateRequest(FileDescriptor fileDescriptor) {
        super("FILE_CREATE:" + fileDescriptor);
        document.append("command", MessageType.FILE_CREATE_REQUEST);
        document.join(fileDescriptor.toJSON());
    }
}
