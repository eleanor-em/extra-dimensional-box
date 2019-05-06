package unimelb.bitbox.messages;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;

public class FileBytesRequest extends Message {
    public FileBytesRequest(Document json, long position) {
        Document fileDescriptor = (Document) json.get("fileDescriptor");
        // ELEANOR: the modulus is a good idea, but it's a bit complicated and I don't think it worked
        // this is simpler and works correctly
        final long BLOCK_SIZE = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        long fileSize = fileDescriptor.getLong("fileSize");
        long bytesLeft = Math.min(fileSize - position, BLOCK_SIZE);

        document.append("command", FILE_BYTES_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", json.getString("pathName"));
        document.append("position", position);
        document.append("length", bytesLeft);
    }

}