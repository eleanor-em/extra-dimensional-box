package unimelb.bitbox.messages;

import unimelb.bitbox.util.network.JSONDocument;

/**
 * @author Benjamin(Jingyi Li) Li
 */
public class FileDeleteRequest extends Message{
    public FileDeleteRequest(JSONDocument fileDescriptor, String pathName){
        super("FILE_DELETE:" + pathName + ":" + fileDescriptor);
        document.append("command", MessageType.FILE_DELETE_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }
}
