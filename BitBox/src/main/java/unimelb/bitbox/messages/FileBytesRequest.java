package unimelb.bitbox.messages;

import unimelb.bitbox.util.config.Configuration;
import unimelb.bitbox.util.fs.FileDescriptor;

/**
 * FILE_BYTES_REQUEST message.
 *
 * @author Eleanor McMurtry
 */
public class FileBytesRequest extends Message {
    public static FileBytesRequest retry(FileBytesResponse response) {
        return new FileBytesRequest(response.fileDescriptor, response.position);
    }

    public FileBytesRequest(FileDescriptor fileDescriptor, long position) {
        super("BYTES:" + fileDescriptor + ":" + position);

        long length = Math.min(fileDescriptor.fileSize() - position, Configuration.getBlockSize());

        document.append("command", MessageType.FILE_BYTES_REQUEST);
        document.join(fileDescriptor.toJSON());
        document.append("position", position);
        document.append("length", length);
    }
}