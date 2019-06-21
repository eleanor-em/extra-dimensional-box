package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.fs.FileManagerException;

/**
 * FILE_DELETE_RESPONSE message.
 *
 * @author Benjamin(Jingyi Li) Li
 * @author Eleanor McMurtry
 */
public class FileDeleteResponse extends Response {
    private static final String SUCCESS = "File deleted";
    private final FileDescriptor fd;
    
    public FileDeleteResponse(FileDescriptor fileDescriptor, Peer peer){
        super("FILE_DELETE:" + fileDescriptor, peer);
        fd = fileDescriptor;

        document.append("command", MessageType.FILE_DELETE_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", fd.pathName);
    }

    @Override
    void onSent() {
        // Try cancelling the file loader first
        String reply = PeerServer.fsManager().cancelFileLoader(fd.pathName)
                  .matchThen(res -> {
                          if (!res) {
                              // if the file wasn't already loading, check that it's a safe pathname
                              if (!PeerServer.fsManager().isSafePathName(fd.pathName)) {
                                  return "unsafe pathname given";
                              }
                              if (!PeerServer.fsManager().fileExists(fd)) {
                                  return "file does not exist";
                              }
                              try {
                                  PeerServer.fsManager().deleteFile(fd);
                              } catch (FileManagerException e) {
                                  return "there was a problem deleting the file: " + e.getMessage();
                              }
                          }
                          return SUCCESS;
                      }, err -> "there was a problem deleting the file: " + err.getMessage());

        boolean successful = reply.equals(SUCCESS);
        if (successful) {
            PeerServer.log().info("Deleting file " + fd.pathName);
        }

        document.append("message", reply);
        document.append("status", successful);
    }
}
