package unimelb.bitbox.util.fs;

import unimelb.bitbox.util.network.JSONData;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.ResponseFormatException;

/**
 * Additional information about a given file.
 */
public class FileDescriptor implements JSONData {
    /**
     * Timestamp of the last modification time of the file.
     */
    public long lastModified;
    /**
     * The MD5 hash of the file's content.
     */
    public String md5;
    /**
     * The size of the file in bytes.
     */
    public long fileSize;

    /**
     * Constructor
     *
     * @param lastModified the timestamp for when file was last modified
     * @param md5          the current MD5 hash of the file's content.
     */
    public FileDescriptor(long lastModified, String md5, long fileSize) {
        this.lastModified = lastModified;
        this.md5 = md5;
        this.fileSize = fileSize;
    }

    public FileDescriptor(JSONDocument doc) throws ResponseFormatException {
        this.lastModified = doc.require("lastModified");
        this.md5 = doc.require("md5");
        this.fileSize = doc.require("fileSize");
    }

    @Override
    public JSONDocument toJSON() {
        JSONDocument doc = new JSONDocument();
        doc.append("lastModified", lastModified);
        doc.append("md5", md5);
        doc.append("fileSize", fileSize);
        return doc;
    }
}
