package unimelb.bitbox.util.fs;

import functional.algebraic.Maybe;
import functional.algebraic.Result;
import unimelb.bitbox.util.network.IJSONData;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

/**
 * Additional information about a given file.
 *
 * @author Aaron Harwood
 * @author Eleanor McMurtry
 */
public class FileDescriptor implements IJSONData {
    private class InternalFD {
        /**
         * Timestamp of the last modification time of the file.
         */
        final long lastModified;
        /**
         * The MD5 hash of the file's content.
         */
        final String md5;
        /**
         * The size of the file in bytes.
         */
        final long fileSize;

        private InternalFD(long lastModified, String md5, long fileSize) {
            this.lastModified = lastModified;
            this.md5 = md5;
            this.fileSize = fileSize;
        }
    }
    public final String pathName;
    public long lastModified() {
        return data.get().lastModified;
    }
    public String md5() {
        return data.get().md5;
    }
    public long fileSize() {
        return data.get().fileSize;
    }

    private final Maybe<InternalFD> data;

    private final boolean isDirectory;

    /**
     * Constructor
     *
     * @param lastModified the timestamp for when file was last modified
     * @param md5          the current MD5 hash of the file's content.
     */
    public FileDescriptor(String pathName, long lastModified, String md5, long fileSize) {
        this.pathName = pathName;
        data = Maybe.just(new InternalFD(lastModified, md5, fileSize));
        isDirectory = false;
    }
    private FileDescriptor(String pathName) {
        isDirectory = true;
        this.pathName = pathName;
        data = Maybe.nothing();
    }

    static FileDescriptor directory(String pathName) {
        return new FileDescriptor(pathName);
    }
    static FileDescriptor rename(FileDescriptor src, String newPathName) {
        if (src.isDirectory) {
            return new FileDescriptor(newPathName);
        }

        return new FileDescriptor(newPathName, src.data.get().lastModified, src.data.get().md5, src.data.get().fileSize);
    }

    /**
     * Produces a FileDescriptor from the given {@link JSONDocument}.
     * @param pathName the file's name
     * @param doc the document to parse
     * @return the file descriptor, or a parsing error
     */
    @SuppressWarnings({"CodeBlock2Expr"})
    public static Result<FileDescriptor, JSONException> fromJSON(String pathName, JSONDocument doc) {
        return doc.getLong("lastModified").andThen(lastModified -> {
            return doc.getLong("fileSize").andThen(fileSize -> {
                return doc.getString("md5").andThen(md5 -> {
                    return Result.value(new FileDescriptor(pathName, lastModified, md5, fileSize));
                });
            });
        });
    }

    /**
     * @return a document with structure {
     *     "pathName": pathName,
     *     "fileDescriptor": {
     *         ...
     *     }
     * }
     */
    @Override
    public JSONDocument toJSON() {
        JSONDocument doc = new JSONDocument();
        if (!isDirectory)  {
            doc.append("fileDescriptor", new JSONDocument()
                    .append("lastModified", lastModified())
                    .append("fileSize", fileSize())
                    .append("md5", md5()));
        }
        return doc.append("pathName", pathName);
    }

    @Override
    public String toString() {
        return encode();
    }

    @Override
    public boolean equals(Object rhs) {
        if (rhs instanceof FileDescriptor) {
            FileDescriptor other = (FileDescriptor) rhs;

            return pathName.equals(other.pathName)
                    && ((other.isDirectory && isDirectory)
                         || (other.data.get().lastModified == data.get().lastModified
                          && data.get().md5.equals(other.data.get().md5)
                          && data.get().fileSize == other.data.get().fileSize));
        }

        return false;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
