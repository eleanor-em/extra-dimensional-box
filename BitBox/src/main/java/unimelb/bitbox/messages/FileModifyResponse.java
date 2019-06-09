package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

import java.io.IOException;


public class FileModifyResponse extends Response {
    private static final String SUCCESS = "file loader ready";
    private final FileDescriptor fd;

    public FileModifyResponse(FileDescriptor fileDescriptor, Peer peer) {
        super("MODIFY:" + fileDescriptor, peer);
        fd = fileDescriptor;

        document.append("command", MessageType.FILE_MODIFY_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", fd.pathName);
    }

    @Override
    void onSent() {
        String reply = SUCCESS;

        try {
            if (!PeerServer.fsManager().isSafePathName(fd.pathName)) {
                reply = "unsafe pathname given";
            } else if (PeerServer.fsManager().fileMatches(fd)) {
                reply = "file already exists with matching content";
            } else if (!PeerServer.fsManager().fileExists(fd)) {
                reply = "file does not exist";
            } else {
                PeerServer.fsManager().modifyFileLoader(fd);
            }
        } catch (IOException e) {
            reply = "there was a problem modifying the file: " + e.getMessage();
        }

        boolean successful = reply.equals(SUCCESS);
        document.append("message", reply);
        document.append("status", successful);
        if (successful) {
            PeerServer.rwManager().addFile(peer, fd);
        }
    }
}
