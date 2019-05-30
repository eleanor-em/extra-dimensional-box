package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

public class FileBytesRequest extends Message {
    public FileBytesRequest(String pathName, FileDescriptor fileDescriptor, long position) {
        super("BYTES:" + pathName + ":" + fileDescriptor + ":" + position);

        long bytesLeft = Math.min(fileDescriptor.fileSize - position, PeerServer.getMaximumLength());

        document.append("command", FILE_BYTES_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
        document.append("position", position);
        document.append("length", bytesLeft);
    }
}