package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.fs.FileManagerException;

public class FileDeleteResponse extends Response {
    private static final String SUCCESS = "File deleted";
    private FileDescriptor fd;
    
    public FileDeleteResponse(FileDescriptor fileDescriptor, Peer peer){
        super("FILE_DELETE:" + fileDescriptor, peer);
        this.fd = fileDescriptor;

        document.append("command", MessageType.FILE_DELETE_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", fd.pathName);
    }

    @Override
    void onSent() {
        // Try cancelling the file loader first
        String reply = PeerServer.fsManager().cancelFileLoader(fd.pathName)
                  .matchThen(
                      err -> "there was a problem deleting the file: " + err.getMessage(),
                      res -> {
                          if (!res) {
                              // if the file wasn't already loading, check that it's a safe pathname
                              if (!PeerServer.fsManager().isSafePathName(fd.pathName)) {
                                  return "unsafe pathname given";
                              }
                              try {
                                  PeerServer.fsManager().deleteFile(fd);
                              } catch (FileManagerException e) {
                                  return "there was a problem deleting the file: " + e.getMessage();
                              }
                          }
                          return SUCCESS;
                      });
        document.append("message", reply);
        document.append("status", reply.equals(SUCCESS));
    }
}
