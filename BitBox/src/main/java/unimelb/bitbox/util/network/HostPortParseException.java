package unimelb.bitbox.util.network;

/**
 * A parsing error that can occur while parsing a {@link HostPort}.
 */
public class HostPortParseException extends Exception {
    HostPortParseException(String failedString) {
        super("Failed to parse host:port string: " + failedString);
    }
}
