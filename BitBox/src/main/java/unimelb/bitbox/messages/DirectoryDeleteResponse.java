package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileManagerException;

/**
 * DIRECTORY_DELETE_RESPONSE message.
 *
 * @author Eleanor McMurtry
 */
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
            reply = "directory does not exist";
        } else {
            try {
                PeerServer.fsManager().deleteDirectory(pathName);
            } catch (FileManagerException e) {
                reply = "there was a problem deleting the directory: " + e.getMessage();
            }
        }

        boolean successful = reply.equals(SUCCESS);
        if (successful) {
            PeerServer.log().info("Deleted directory " + pathName);
        }

        document.append("message", reply);
        document.append("status", successful);
    }
}
