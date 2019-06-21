package unimelb.bitbox.util.fs;

import java.io.IOException;

/**
 * An exception that can be caused by the file manager.
 */
public class FileManagerException extends IOException {
    FileManagerException(String cause) { super(cause); }

    static void check(boolean result, String messageIfFailed) throws FileManagerException {
        if (!result) {
            throw new FileManagerException(messageIfFailed);
        }
    }
}
