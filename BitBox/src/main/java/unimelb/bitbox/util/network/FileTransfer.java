package unimelb.bitbox.util.network;

import unimelb.bitbox.messages.FileBytesRequest;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

public class FileTransfer {
    public final Peer peer;
    public final FileDescriptor fileDescriptor;
    private boolean sentInitialRequest = false;

    public FileTransfer(Peer peer, FileDescriptor fileDescriptor) {
        this.peer = peer;
        this.fileDescriptor = fileDescriptor;
    }

    public String pathName() {
        return fileDescriptor.pathName;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof FileTransfer) {
            FileTransfer rhs = (FileTransfer) other;
            return fileDescriptor.equals(rhs.fileDescriptor) && peer.equals(rhs.peer);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (fileDescriptor.toString() + peer.toString()).hashCode();
    }

    public void sendInitialBytesRequest() {
        if (!sentInitialRequest) {
            sentInitialRequest = true;
            peer.sendMessage(new FileBytesRequest(fileDescriptor.pathName, fileDescriptor, 0));
            PeerServer.log().info(peer.getForeignName() + ": sent FILE_BYTES_REQUEST for " +
                                   fileDescriptor.pathName + " at position: [0/" + fileDescriptor.fileSize + "]");
        } else {
            PeerServer.log().severe("Tried to send initial request, but was already sent");
        }
    }
}
