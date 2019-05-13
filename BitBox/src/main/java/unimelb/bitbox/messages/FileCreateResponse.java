package unimelb.bitbox.messages;

import unimelb.bitbox.ServerMain;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.ResponseFormatException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class FileCreateResponse extends Message {
    private static final String SUCCESS = "file loader ready";

    public final boolean successful;
    public FileCreateResponse(FileSystemManager fsManager, String pathName, JsonDocument fileDescriptor) {
        String reply;
        try {
            if (!fsManager.isSafePathName(pathName)) {
                reply = "unsafe pathname given: " + pathName;
            } else {
                reply = generateFileLoader(fsManager, pathName, fileDescriptor);
            }
        } catch (Exception e) {
            reply = "there was a problem creating the file: " + pathName;
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
    private String generateFileLoader(FileSystemManager fsManager, String pathName, JsonDocument fileDescriptor)
        throws ResponseFormatException {
        String md5 = fileDescriptor.require("md5");
        long length = fileDescriptor.require("fileSize");
        long lastModified = fileDescriptor.require("lastModified");
        try {
            boolean done = fsManager.createFileLoader(pathName, md5, length, lastModified);
            if (done){
                ServerMain.log.info(": file loader for " + pathName + " is ready");
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
        catch (IOException | NullPointerException | NoSuchAlgorithmException e){
            ServerMain.log.severe("error generating file loader for " + pathName);
            return "misc error: " + e.getMessage() + ": " + pathName;
        }
    }
}
