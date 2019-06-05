package unimelb.bitbox.util.network;

public class HostPortParseException extends Exception {
    HostPortParseException(String failedString) {
        super("Failed to parse host:port string: " + failedString);
    }
}
