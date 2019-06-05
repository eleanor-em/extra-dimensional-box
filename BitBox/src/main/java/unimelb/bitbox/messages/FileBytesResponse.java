package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.network.FilePacket;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

public class FileBytesResponse extends Response {
    private static final String SUCCESS = "successful read";
    final FileDescriptor fileDescriptor;
    final String pathName;
    final long position;
    final long length;

    public FileBytesResponse(FilePacket packet) {
        super("BYTES:" + packet.fd() + ":" + packet.position, packet.peer());
        this.fileDescriptor = packet.fd();
        this.pathName = packet.pathName();
        this.position = packet.position;
        this.length = packet.length;

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
                                          PeerServer.log().warning(peer + ": failed reading bytes of file " + pathName +
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
    }
}
