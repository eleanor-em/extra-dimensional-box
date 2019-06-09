package unimelb.bitbox.util.fs;

import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.IJSONData;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import javax.print.DocFlavor;

/**
 * Additional information about a given file.
 */
public class FileDescriptor implements IJSONData {
    // TODO: Add method to return a JSON representation with the pathname
    /**
     * Timestamp of the last modification time of the file.
     */
    public long lastModified = 0L;
    /**
     * The MD5 hash of the file's content.
     */
    public String md5 = null;
    /**
     * The size of the file in bytes.
     */
    public long fileSize = 0L;
    public String pathName;

    private final boolean isDirectory;

    /**
     * Constructor
     *
     * @param lastModified the timestamp for when file was last modified
     * @param md5          the current MD5 hash of the file's content.
     */
    public FileDescriptor(String pathName, long lastModified, String md5, long fileSize) {
        this.pathName = pathName;
        this.lastModified = lastModified;
        this.md5 = md5;
        this.fileSize = fileSize;
        isDirectory = false;
    }

    public String humanFileSize() {
        String[] suffixes = { "B", "kB", "MB", "GB"};
        int suffixIndex = 0;

        float size = fileSize;
        while (size > 1024 && suffixIndex < suffixes.length) {
            ++suffixIndex;
            size /= 1024;
        }

        return String.format("%.1f %s", size, suffixes[suffixIndex]);
    }

    static FileDescriptor directory(String pathName) {
        return new FileDescriptor(pathName);
    }
    static FileDescriptor rename(FileDescriptor src, String newPathName) {
        if (src.isDirectory) {
            return new FileDescriptor(newPathName);
        }

        return new FileDescriptor(newPathName, src.lastModified, src.md5, src.fileSize);
    }

    @SuppressWarnings({"CodeBlock2Expr"})
    public static Result<JSONException, FileDescriptor> fromJSON(String pathName, JSONDocument doc) {
        return doc.getLong("lastModified").andThen(lastModified -> {
            return doc.getLong("fileSize").andThen(fileSize -> {
                return doc.getString("md5").andThen(md5 -> {
                    return Result.value(new FileDescriptor(pathName, lastModified, md5, fileSize));
                });
            });
        });
    }

    private FileDescriptor(String pathName) {
        isDirectory = true;
        this.pathName = pathName;
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
        return pathName + ": " + toJSON();
    }
}
