package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;

import java.io.IOException;


public class FileModifyResponse extends Response {
    private static final String SUCCESS = "file loader ready";
    private String pathName;
    private FileDescriptor fileDescriptor;

    private boolean successful;
    public boolean isSuccessful() {
        return successful;
    }

    public FileModifyResponse(FileDescriptor fileDescriptor, String pathName) {
        super("MODIFY:" + pathName + ":" + fileDescriptor);
        this.pathName = pathName;
        this.fileDescriptor = fileDescriptor;

        document.append("command", FILE_MODIFY_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
    }

    @Override
    void onSent() {
        String reply = SUCCESS;
        try {
            if (!PeerServer.fsManager().isSafePathName(pathName)) {
                reply = "unsafe pathname given";
            } else if (PeerServer.fsManager().fileNameExists(pathName, fileDescriptor.md5)) {
                reply = "file already exists with matching content";
            } else if (!PeerServer.fsManager().fileNameExists(pathName)) {
                reply = "pathname does not exist";
            } else {
                PeerServer.fsManager().modifyFileLoader(pathName, fileDescriptor);
            }
        } catch (IOException e) {
            reply = "there was a problem modifying the file";
        }

        successful = reply.equals(SUCCESS);
        document.append("message", reply);
        document.append("status", successful);
    }
}
