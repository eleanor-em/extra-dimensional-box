package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

public class FileBytesResponse extends Response {
    private static final String SUCCESS = "successful read";
    private FileDescriptor fileDescriptor;
    private String pathName;
    private long position;
    private String content;
    private long length;

    public FileBytesResponse(String pathName, FileDescriptor fileDescriptor, long length, long position, Peer peer) {
        super("BYTES:" + pathName + ":" + fileDescriptor + ":" + position, peer);
        this.fileDescriptor = fileDescriptor;
        this.pathName = pathName;
        this.position = position;
        this.length = length;

        // Prepare the message
        document.append("command", MessageType.FILE_BYTES_RESPONSE);
        document.append("fileDescriptor", fileDescriptor.toJSON());
        document.append("pathName", pathName);
        document.append("length", length);
        document.append("position", position);
    }

    @Override
    void onSent() {
        AtomicReference<String> content = new AtomicReference<>();
        String reply = PeerServer.fsManager().readFile(fileDescriptor.md5, position, length)
                              .matchThen(error -> {
                                          PeerServer.logWarning(peer + ": failed reading bytes of file " + pathName +
                                                                " at [" + position + "/" + fileDescriptor.fileSize + "]: "
                                                                + error.getMessage());
                                          return "failed to read bytes: " + error.getMessage();
                                      }, byteBuffer -> {
                                          content.set(Base64.getEncoder().encodeToString(byteBuffer.array()));
                                          return SUCCESS;
                                      });

        boolean successful = reply.equals(SUCCESS);
        document.append("content", content.get());
        document.append("message", reply);
        document.append("status", successful);

        if (successful) {
            PeerServer.rwManager().writeFile(peer, fileDescriptor, pathName, position, length, content.get());
        } else {
            // Let's try to read the bytes again!
            PeerServer.logInfo("Retrying byte request for " + pathName);
            PeerServer.rwManager().sendReadRequest(peer, pathName, fileDescriptor, position);
        }
    }
}
