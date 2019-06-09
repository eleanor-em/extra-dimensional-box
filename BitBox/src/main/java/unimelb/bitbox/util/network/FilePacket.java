package unimelb.bitbox.util.network;

import unimelb.bitbox.messages.FileBytesRequest;
import unimelb.bitbox.messages.FileBytesResponse;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

import java.io.IOException;
import java.nio.ByteBuffer;

public class FilePacket {
    public final FileTransfer transfer;
    public final long position;
    public final long length;

    public FilePacket(Peer peer, FileDescriptor fileDescriptor, long position, long length) {
        transfer = new FileTransfer(peer, fileDescriptor);
        this.position = position;
        this.length = Math.min(PeerServer.maxBlockSize(), length);
    }

    public Peer peer() {
        return transfer.peer;
    }

    public FileDescriptor fd() {
        return transfer.fileDescriptor;
    }

    public String pathName() {
        return transfer.pathName();
    }
    private String shortPathName() {
        String[] split = pathName().split("/");
        return split[split.length - 1];
    }

    public void sendBytesResponse() {
        transfer.peer.sendMessage(new FileBytesResponse(this));
    }

    public void sendBytesRequest() {
        long nextPosition = position + length;
        transfer.peer.sendMessage(new FileBytesRequest(pathName(), fd(), nextPosition));
        float completion = (float) nextPosition / (float) fd().fileSize * 100;
        String completionPercent = String.format("%.1f", completion);
        PeerServer.log().info("Downloading " + shortPathName() + " (" + completionPercent + "% complete, total "
                              + Conversion.humanFileSize(fd().fileSize) + ")");
        PeerServer.log().fine(peer().getForeignName() + ": requesting bytes for " + pathName() +
                " at position: [" + nextPosition + "/" + fd().fileSize + "]");
    }

    public void writeData(ByteBuffer decoded) throws IOException {
        PeerServer.fsManager().writeFile(pathName(), decoded, position);
    }
}
