package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

import java.io.IOException;

public class FileCreateResponse extends Response {
    private static final String SUCCESS = "file loader ready";
    private final FileDescriptor fd;

    public FileCreateResponse(FileDescriptor fileDescriptor, Peer peer) {
        super("FILE_CREATE:" + fileDescriptor, peer);
        fd = fileDescriptor;

        document.append("command", MessageType.FILE_CREATE_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", fd.pathName);
    }

    private String generateFileLoader() {
        try {
            PeerServer.fsManager().createFileLoader(fd);
        } catch (IOException e) {
            // We possibly have a different version of this file.
            if (PeerServer.fsManager().fileExists(fd)) {
                try {
                    PeerServer.fsManager().modifyFileLoader(fd);
                } catch (IOException ignored) {
                    // We're currently transferring this file, or else our file is newer.
                    PeerServer.log().warning("failed to generate file loader for " + fd.pathName);
                    return "error generating modify file loader: " + fd.pathName;
                }
            } else {
                return "error generating create file loader: " + fd.pathName;
            }
        }
        return SUCCESS;
    }

    @Override
    void onSent() {
        String reply;
        if (PeerServer.fsManager().fileMatches(fd)) {
            reply = "file already exists locally";
        } else if (!PeerServer.fsManager().isSafePathName(fd.pathName)) {
            reply = "unsafe pathname given: " + fd.pathName;
        } else {
            reply = generateFileLoader();
        }

        boolean successful = reply.equals(SUCCESS);
        document.append("message", reply);
        document.append("status", successful);

        if (successful) {
            // Check if this file is already elsewhere on disk
            PeerServer.fsManager().checkShortcut(fd)
                      .match(err -> PeerServer.log().severe(peer.getForeignName() + ": error checking shortcut for " + fd.pathName),
                          res -> {
                          if (!res) {
                              PeerServer.log().info(peer.getForeignName() + ": file " + fd.pathName +
                                      " not available locally. Send a FILE_BYTES_REQUEST");
                              PeerServer.rwManager().addFile(peer, fd);
                          }
                      });
        }
    }
}
