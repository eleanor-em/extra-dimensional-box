package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileDescriptor;

/**
 * FILE_DELETE_REQUEST message.
 *
 * @author Eleanor McMurtry
 * @author Benjamin(Jingyi Li) Li
 */
public class FileDeleteRequest extends Message{
    public FileDeleteRequest(FileDescriptor fileDescriptor){
        super("FILE_DELETE:" + fileDescriptor);
        document.append("command", MessageType.FILE_DELETE_REQUEST);
        document.join(fileDescriptor.toJSON());
    }
}
