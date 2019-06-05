package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileDescriptor;

/**
 * @author Benjamin(Jingyi Li) Li
 */
public class FileDeleteRequest extends Message{
    public FileDeleteRequest(FileDescriptor fd){
        super("FILE_DELETE:" + fd);
        document.append("command", MessageType.FILE_DELETE_REQUEST);
        document.append("fileDescriptor", fd);
        document.append("pathName", fd.pathName);
    }
}
