package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.fs.FileSystemException;
import unimelb.bitbox.util.fs.FileSystemManager;
import unimelb.bitbox.util.network.JSONException;

import java.io.IOException;

public class FileCreateResponse extends Message {
    private static final String SUCCESS = "file loader ready";

    public final boolean successful;
    public FileCreateResponse(FileSystemManager fsManager, String pathName, FileDescriptor fileDescriptor, long length, boolean dryRun)
            throws JSONException {
        super("FILE_CREATE:" + pathName + ":" + fileDescriptor);
        if (dryRun) {
            successful = false;
            return;
        }
        String reply;


        if (fsManager.fileNameExists(pathName, fileDescriptor.md5)) {
            reply = "file already exists locally";
        } else if (!fsManager.isSafePathName(pathName)) {
            reply = "unsafe pathname given: " + pathName;
        } else {
            reply = generateFileLoader(fsManager, pathName, fileDescriptor, length);
        }

        successful = reply.equals(SUCCESS);
        document.append("command", FILE_CREATE_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
        document.append("message", reply);
        document.append("status", successful);
    }

    private String generateFileLoader(FileSystemManager fsManager, String pathName, FileDescriptor fileDescriptor, long length) {
        try {
            fsManager.createFileLoader(pathName, fileDescriptor.md5, length, fileDescriptor.lastModified);
        } catch (FileSystemException e) {
            // We possibly have a different version of this file.
            if (fsManager.fileNameExists(pathName)) {
                try {
                    fsManager.modifyFileLoader(pathName, fileDescriptor.md5, fileDescriptor.lastModified, length);
                } catch (IOException ignored) {
                    // We're currently transferring this file, or else our file is newer.
                    PeerServer.log.warning("failed to generate file loader for " + pathName);
                    return "error generating modify file loader: " + pathName;
                }
            } else {
                e.printStackTrace();
                return "error generating create file loader: " + pathName;
            }
        }
        catch (Exception e){
            PeerServer.log.severe("error generating file loader for " + pathName);
            e.printStackTrace();
            return "misc error: " + e.getMessage() + ": " + pathName;
        }
        return SUCCESS;
    }
}
