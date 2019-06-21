package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileDescriptor;

/**
 * FILE_CREATE_REQUEST message.
 *
 * @author Eleanor McMurtry
 */
public class FileCreateRequest extends Message {
    public FileCreateRequest(FileDescriptor fd) {
        super("FILE_CREATE:" + fd);
        document.append("command", MessageType.FILE_CREATE_REQUEST);
        document.append("fileDescriptor", fd);
        document.append("pathName", fd.pathName);
    }
}
