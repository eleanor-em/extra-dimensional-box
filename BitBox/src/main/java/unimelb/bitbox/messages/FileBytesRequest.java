package unimelb.bitbox.messages;

import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.ResponseFormatException;

public class FileBytesRequest extends Message {
    public FileBytesRequest(ServerMain server, String pathName, JSONDocument fileDescriptor, long position) throws ResponseFormatException {
        super("BYTES:" + pathName + ":" + fileDescriptor + ":" + position);
        // ELEANOR: the modulus is a good idea, but it's a bit complicated and I don't think it worked
        // this is simpler and works correctly
        long fileSize = fileDescriptor.require("fileSize");
        long bytesLeft = Math.min(fileSize - position, server.getBlockSize());

        document.append("command", FILE_BYTES_REQUEST);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
        document.append("position", position);
        document.append("length", bytesLeft);

    }

}