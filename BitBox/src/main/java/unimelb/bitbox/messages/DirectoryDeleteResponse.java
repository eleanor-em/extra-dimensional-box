package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileSystemException;

public class DirectoryDeleteResponse extends Response {
    private static final String SUCCESS = "directory deleted";
    private final String pathName;

    public DirectoryDeleteResponse(String pathName, Peer peer) {
        super("DIRECTORY_DELETE:" + pathName, peer);
        this.pathName = pathName;

        document.append("command", MessageType.DIRECTORY_DELETE_RESPONSE);
        document.append("pathName", pathName);
    }

    @Override
    void onSent() {
        String reply = SUCCESS;
        if (!PeerServer.fsManager().isSafePathName(pathName)) {
            reply = "unsafe pathname given";
        } else if (!PeerServer.fsManager().dirNameExists(pathName)) {
            reply = "pathname does not exist";
        } else {
            try {
                PeerServer.fsManager().deleteDirectory(pathName);
            } catch (FileSystemException e) {
                reply = "there was a problem deleting the directory";
            }
        }
        document.append("message", reply);
        document.append("status", reply.equals(SUCCESS));
    }
}
