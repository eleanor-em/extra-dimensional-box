package unimelb.bitbox.messages;

import unimelb.bitbox.util.network.JsonDocument;

/**
 * @Auther Benjamin(Jingyi Li) Li
 * @Email jili@student.unimelb.edu.au
 * @ID 961543
 * @Date 2019-04-19 17:25
 */
public class FileDeleteRequest extends Message{
    public FileDeleteRequest(JsonDocument fileDescriptor, String pathName){
        super("FILE_DELETE:" + pathName + ":" + fileDescriptor.toJson());
        document.append("command", FILE_DELETE_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }
}
