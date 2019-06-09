package unimelb.bitbox.util.network;

public class Conversion {
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
