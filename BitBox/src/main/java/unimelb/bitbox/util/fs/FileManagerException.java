package unimelb.bitbox.util.fs;

import java.io.IOException;

public class FileManagerException extends IOException {
    public FileManagerException(String cause) { super(cause); }
    public FileManagerException(IOException cause) { super(cause); }

    public static void check(boolean result, String messageIfFailed) throws FileManagerException {
        if (!result) {
            throw new FileManagerException(messageIfFailed);
        }
    }
}
