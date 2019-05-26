package unimelb.bitbox.util.network;

public class HostPortParseException extends Exception {
    public HostPortParseException(String failedString) {
        super("Failed to parse host:port string: " + failedString);
    }
}
