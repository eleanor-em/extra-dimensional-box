package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.network.FilePacket;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FileBytesResponse extends Response {
    private static final String SUCCESS = "successful read";
    final FileDescriptor fileDescriptor;
    final String pathName;
    final long position;
    private final long length;

    public FileBytesResponse(FilePacket packet) {
        super("BYTES:" + packet.fd() + ":" + packet.position, packet.peer());
        fileDescriptor = packet.fd();
        pathName = packet.pathName();
        position = packet.position;
        length = packet.length;

        // Prepare the message
        document.append("command", MessageType.FILE_BYTES_RESPONSE);
        document.append("fileDescriptor", fileDescriptor.toJSON());
        document.append("pathName", pathName);
        document.append("length", length);
        document.append("position", position);
    }

    @Override
    void onSent() {
        AtomicReference<String> content = new AtomicReference<>("");
        AtomicBoolean shouldRetry = new AtomicBoolean(true);

        String reply = PeerServer.fsManager().readFile(fileDescriptor.md5(), position, length)
                                 .matchThen(maybeBuffer -> maybeBuffer.matchThen(
                                              byteBuffer -> {
                                                  content.set(Base64.getEncoder().encodeToString(byteBuffer.array()));
                                                  return SUCCESS;
                                              },
                                              () -> {
                                                  // If the file was missing, there's no point retrying
                                                  shouldRetry.set(false);
                                                  return "file not found";
                                              }),
                                         error -> {
                                             // If reading caused an error, we can probably retry later
                                             PeerServer.log().warning(peer + ": failed reading bytes of file " + pathName +
                                                     " at [" + position + "/" + fileDescriptor.fileSize() + "]: "
                                                     + error.getMessage());
                                             return "failed to read bytes: " + error.getMessage();
                                         });

        boolean successful = reply.equals(SUCCESS);
        if (successful) {
            // Don't tell the peer to retry a successful request
            shouldRetry.set(false);
        }

        document.append("content", content.get());
        document.append("message", reply);
        document.append("status", successful);
        document.append("retry", shouldRetry.get());
    }
}
