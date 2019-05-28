package unimelb.bitbox.messages;

import unimelb.bitbox.util.fs.FileDescriptor;

public class FileBytesResponse extends Message {
    public static final String SUCCESS = "successful read";
    public final boolean successful;

    public FileBytesResponse(FileDescriptor fileDescriptor, String pathName, long length, long position, String content, String reply, boolean dryRun) {
        super("BYTES:" + pathName + ":" + fileDescriptor + ":" + position);

        successful = reply.equals(SUCCESS);
        if (dryRun) {
            return;
        }

        // Prepare the message
        document.append("command", FILE_BYTES_RESPONSE);
        document.append("fileDescriptor", fileDescriptor.toJSON());
        document.append("pathName", pathName);
        document.append("length", length);
        document.append("content", content);
        document.append("position", position);
        document.append("message", reply);
        document.append("status", successful);
    }
}
