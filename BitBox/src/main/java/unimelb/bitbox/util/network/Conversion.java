package unimelb.bitbox.util.network;

/**
 * A utility class with network conversion operations.
 */
public class Conversion {
    /**
     * Converts a file size in bytes to a human-readable form (e.g. 1.1GB).
     */
    public static String humanFileSize(long fileSize) {
        String[] suffixes = { "B", "kB", "MB", "GB"};
        int suffixIndex = 0;

        float size = fileSize;
        while (size > 1024 && suffixIndex < suffixes.length) {
            ++suffixIndex;
            size /= 1024;
        }

        return String.format("%.1f %s", size, suffixes[suffixIndex]);
    }
}
