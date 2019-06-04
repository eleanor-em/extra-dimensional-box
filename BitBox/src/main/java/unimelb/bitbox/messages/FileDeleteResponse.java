package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.fs.FileSystemException;

public class FileDeleteResponse extends Response {
    private static final String SUCCESS = "File deleted";
    private String pathName;
    private FileDescriptor fileDescriptor;
    
    public FileDeleteResponse(String pathName, FileDescriptor fileDescriptor, Peer peer){
        super("FILE_DELETE:" + pathName + ":" + fileDescriptor, peer);
        this.pathName = pathName;
        this.fileDescriptor = fileDescriptor;

        document.append("command", MessageType.FILE_DELETE_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }

    @Override
    void onSent() {
        // Try cancelling the file loader first
        String reply = PeerServer.fsManager().cancelFileLoader(pathName)
                  .matchThen(
                      err -> "there was a problem deleting the file: " + err.getMessage(),
                      res -> {
                          if (!res) {
                              // if the file wasn't already loading, check that it's a safe pathname
                              if (!PeerServer.fsManager().isSafePathName(pathName)) {
                                  return "unsafe pathname given";
                              }
                              try {
                                  PeerServer.fsManager().deleteFile(pathName, fileDescriptor.lastModified, fileDescriptor.md5);
                              } catch (FileSystemException e) {
                                  return "there was a problem deleting the file: " + e.getMessage();
                              }
                          }
                          return SUCCESS;
                      });
        document.append("message", reply);
        document.append("status", reply.equals(SUCCESS));
    }
}
