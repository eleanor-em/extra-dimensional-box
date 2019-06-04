package unimelb.bitbox.util.fs;

import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.IJSONData;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

/**
 * Additional information about a given file.
 */
public class FileDescriptor implements IJSONData {
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

    public final boolean isDirectory;

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
        isDirectory = false;
    }

    public static FileDescriptor directory() {
        return new FileDescriptor();
    }

    @SuppressWarnings({"CodeBlock2Expr"})
    public static Result<JSONException, FileDescriptor> fromJSON(JSONDocument doc) {
        return doc.getLong("lastModified").andThen(lastModified -> {
            return doc.getLong("fileSize").andThen(fileSize -> {
                return doc.getString("md5").andThen(md5 -> {
                    return Result.value(new FileDescriptor(lastModified, md5, fileSize));
                });
            });
        });
    }

    private FileDescriptor() {
        isDirectory = true;
    }

    @Override
    public JSONDocument toJSON() {
        JSONDocument doc = new JSONDocument();
        doc.append("lastModified", lastModified);
        doc.append("md5", md5);
        doc.append("fileSize", fileSize);
        return doc;
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }
}
