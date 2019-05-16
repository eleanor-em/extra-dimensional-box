package unimelb.bitbox.messages;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.Document;

public class FileBytesResponse extends Message {
    public static String SUCCESS = "successful read";

    public FileBytesResponse(Document json, String pathName, long length, long position, String content, String reply) {
        Document fileDescriptor = (Document) json.get("fileDescriptor");

        // Prepare the message
        document.append("command", FILE_BYTES_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
        document.append("length", length);
        document.append("content", content);
        document.append("position", position);
        document.append("message", reply);
        document.append("status", reply == SUCCESS);
    }
}
