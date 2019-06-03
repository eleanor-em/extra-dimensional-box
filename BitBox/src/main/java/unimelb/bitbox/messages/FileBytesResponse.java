package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

public class FileBytesResponse extends Response {
    public static final String SUCCESS = "successful read";
    private final boolean successful;
    private FileDescriptor fileDescriptor;
    private String pathName;
    private long position;
    private String content;
    private long length;

    public FileBytesResponse(String pathName, FileDescriptor fileDescriptor, long length, long position, String content, String reply, Peer peer) {
        super("BYTES:" + pathName + ":" + fileDescriptor + ":" + position, peer);
        successful = reply.equals(SUCCESS);
        this.fileDescriptor = fileDescriptor;
        this.pathName = pathName;
        this.position = position;
        this.content = content;
        this.length = length;

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

    @Override
    void onSent() {
        if (successful) {
            PeerServer.rwManager().writeFile(peer, fileDescriptor, pathName, position, length, content);
        } else {
            // Let's try to read the bytes again!
            PeerServer.logInfo("Retrying byte request for " + pathName);
            PeerServer.rwManager().sendReadRequest(peer, pathName, fileDescriptor, position);
        }
    }
}
