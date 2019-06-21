package unimelb.bitbox.util.network;

import functional.algebraic.Maybe;
import unimelb.bitbox.messages.FileBytesRequest;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

/**
 * Represents a file that is being transferred.
 */
public class FileTransfer {
    /**
     * The peer that is doing the transfer.
     */
    public final Peer peer;
    /**
     * The file that is being transferred.
     */
    public final FileDescriptor fileDescriptor;

    private Maybe<FilePacket> packet = Maybe.nothing();

    /**
     * Create a file transfer for a given peer and file.
     */
    public FileTransfer(Peer peer, FileDescriptor fileDescriptor) {
        this.peer = peer;
        this.fileDescriptor = fileDescriptor;
    }

    public String pathName() {
        return fileDescriptor.pathName;
    }

    /**
     * Set a new packet as the current packet of the transfer.
     */
    public void updatePacket(FilePacket packet) {
        this.packet = Maybe.just(packet);
    }

    /**
     * Returns the percentage of completion for this transfer.
     */
    public float getCompletion() {
        if (!packet.isJust()) {
            return 0;
        }
        return 100 * (float) packet.get().position / (float) fileDescriptor.fileSize();
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
        peer.sendMessage(new FileBytesRequest(fileDescriptor.pathName, fileDescriptor, 0));
        PeerServer.log().info("Beginning download of " + fileDescriptor.pathName
                              + " (" + Conversion.humanFileSize(fileDescriptor.fileSize()) + ")");
        PeerServer.log().fine(peer.getForeignName() + ": sent FILE_BYTES_REQUEST for " +
                               fileDescriptor.pathName + " at position: [0/" + fileDescriptor.fileSize() + "]");
    }
}
