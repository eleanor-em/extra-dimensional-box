package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

/**
 * FILE_BYTES_REQUEST message.
 *
 * @author Eleanor McMurtry
 */
public class FileBytesRequest extends Message {
    public static FileBytesRequest retry(FileBytesResponse response) {
        return new FileBytesRequest(response.pathName, response.fileDescriptor, response.position);
    }

    public FileBytesRequest(String pathName, FileDescriptor fileDescriptor, long position) {
        super("BYTES:" + fileDescriptor + ":" + position);

        long length = Math.min(fileDescriptor.fileSize() - position, PeerServer.maxBlockSize());

        document.append("command", MessageType.FILE_BYTES_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
        document.append("position", position);
        document.append("length", length);
    }
}