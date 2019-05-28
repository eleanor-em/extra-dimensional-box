package unimelb.bitbox.util.fs;

import java.io.IOException;

public class FileSystemException extends IOException {
    public FileSystemException(String cause) { super(cause); }
    public FileSystemException(IOException cause) { super(cause); }

    public static void check(boolean result, String messageIfFailed) throws FileSystemException {
        if (!result) {
            throw new FileSystemException(messageIfFailed);
        }
    }
}
