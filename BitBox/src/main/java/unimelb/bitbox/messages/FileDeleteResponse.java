package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

import java.io.IOException;

public class FileDeleteResponse extends Response {
    private static final String SUCCESS = "File deleted";
    private String pathName;
    private FileDescriptor fileDescriptor;
    
    public FileDeleteResponse(String pathName, FileDescriptor fileDescriptor, Peer peer){
        super("FILE_DELETE:" + pathName + ":" + fileDescriptor, peer);
        this.pathName = pathName;
        this.fileDescriptor = fileDescriptor;

        document.append("command", FILE_DELETE_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }

    @Override
    void onSent() {
        String reply = SUCCESS;
        try {
            // Try cancelling the file loader first
            if (!PeerServer.fsManager().cancelFileLoader(pathName)) {
                if (!PeerServer.fsManager().isSafePathName(pathName)) {
                    reply = "unsafe pathname given";
                } else if (!PeerServer.fsManager().fileNameExists(pathName)) {
                    reply = "pathname does not exist";
                }
                PeerServer.fsManager().deleteFile(pathName, fileDescriptor.lastModified, fileDescriptor.md5);
            }
        } catch (IOException e) {
            reply = "there was a problem deleting the file: " + e.getMessage();
        }
        document.append("message", reply);
        document.append("status", reply.equals(SUCCESS));
    }
}
