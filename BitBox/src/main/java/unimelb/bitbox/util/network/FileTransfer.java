package unimelb.bitbox.util.network;

import unimelb.bitbox.messages.FileBytesRequest;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

public class FileTransfer {
    public final Peer peer;
    public final FileDescriptor fileDescriptor;

    private FilePacket packet;
    private boolean waitingToSend = true;

    public FileTransfer(Peer peer, FileDescriptor fileDescriptor) {
        this.peer = peer;
        this.fileDescriptor = fileDescriptor;
    }

    public String pathName() {
        return fileDescriptor.pathName;
    }

    public void updatePacket(FilePacket packet) {
        this.packet = packet;
    }

    public float getCompletion() {
        if (packet == null) {
            return 0;
        }

        return 100 * (float) packet.position / (float) fileDescriptor.fileSize;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FileTransfer && fileDescriptor.equals(((FileTransfer) other).fileDescriptor);
    }

    @Override
    public String toString() {
        return fileDescriptor + " via " + peer;
    }

    @Override
    public int hashCode() {
        return fileDescriptor.hashCode();
    }

    public void sendInitialBytesRequest() {
        if (waitingToSend) {
            waitingToSend = false;
            peer.sendMessage(new FileBytesRequest(fileDescriptor.pathName, fileDescriptor, 0));
            PeerServer.log().info("Beginning download of " + fileDescriptor.pathName
                                  + " (" + Conversion.humanFileSize(fileDescriptor.fileSize) + ")");
            PeerServer.log().fine(peer.getForeignName() + ": sent FILE_BYTES_REQUEST for " +
                                   fileDescriptor.pathName + " at position: [0/" + fileDescriptor.fileSize + "]");
        } else {
            PeerServer.log().severe("Tried to send initial request, but was already sent");
        }
    }
}
