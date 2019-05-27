package unimelb.bitbox.messages;

import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.util.fs.FileSystemManager;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.ResponseFormatException;

public class FileCreateResponse extends Message {
    private static final String SUCCESS = "file loader ready";

    public final boolean successful;
    public FileCreateResponse(FileSystemManager fsManager, String pathName, JSONDocument fileDescriptor, boolean dryRun)
            throws ResponseFormatException {
        super("FILE_CREATE:" + pathName + ":" + fileDescriptor);
        if (dryRun) {
            successful = false;
            return;
        }
        String reply;

        if (fileAlreadyExists(fileDescriptor, pathName, fsManager)) {
            reply = "file already exists locally";
        } else if (!fsManager.isSafePathName(pathName)) {
            reply = "unsafe pathname given: " + pathName;
        } else {
            reply = generateFileLoader(fsManager, pathName, fileDescriptor);
        }

        successful = reply == SUCCESS;
        document.append("command", FILE_CREATE_RESPONSE);
        document.append("fileDescriptor", fileDescriptor);
        document.append("pathName", pathName);
        document.append("message", reply);
        document.append("status", successful);
    }

    // ELEANOR: Moved this method here so that we aren't creating two loaders, and so we can check the loader for
    //          errors before responding.
    private String generateFileLoader(FileSystemManager fsManager, String pathName, JSONDocument fileDescriptor)
        throws ResponseFormatException {
        String md5 = fileDescriptor.require("md5");
        long length = fileDescriptor.require("fileSize");
        long lastModified = fileDescriptor.require("lastModified");
        try {
            boolean done = fsManager.createFileLoader(pathName, md5, length, lastModified);
            if (done){
                return SUCCESS;
            } else {
                // We possibly have a different version of this file.
                if (fsManager.fileNameExists(pathName)) {
                    if (!fsManager.modifyFileLoader(pathName, md5, lastModified, length)) {
                        // We're currently transferring this file, or else our file is newer.
                        ServerMain.log.warning("failed to generate file loader for " + pathName);
                        return "error generating modify file loader: " + pathName;
                    } else {
                        return SUCCESS;
                    }
                } else {
                    return "error generating create file loader: " + pathName;
                }
            }
        }
        catch (Exception e){
            ServerMain.log.severe("error generating file loader for " + pathName);
            e.printStackTrace();
            return "misc error: " + e.getMessage() + ": " + pathName;
        }
    }

    /**
     * This method checks if a file was created with the same name and content.
     */
    private boolean fileAlreadyExists(JSONDocument fileDescriptor, String pathName, FileSystemManager fsManager)
            throws ResponseFormatException {

        return fsManager.fileNameExists(pathName, fileDescriptor.require("md5"));
    }
}
