package unimelb.bitbox.messages;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.network.JsonDocument;
import unimelb.bitbox.util.network.ResponseFormatException;

public class FileBytesRequest extends Message {
    public FileBytesRequest(String pathName, JsonDocument fileDescriptor, long position) throws ResponseFormatException {
        super("BYTES:" + pathName + ":" + fileDescriptor.toJson() + ":" + position);
        // ELEANOR: the modulus is a good idea, but it's a bit complicated and I don't think it worked
        // this is simpler and works correctly
        long fileSize = fileDescriptor.require("fileSize");
        long bytesLeft = Math.min(fileSize - position, ServerMain.getBlockSize());

        document.append("command", FILE_BYTES_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
        document.append("position", position);
        document.append("length", bytesLeft);

    }

}