package unimelb.bitbox.util;

public class HostPortParseException extends Exception {
    public HostPortParseException(String failedString) {
        super("Failed to parse host:port string: " + failedString);
    }
}
