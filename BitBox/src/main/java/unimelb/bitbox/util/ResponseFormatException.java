package unimelb.bitbox.util;

/**
 * Thrown in case of a malformed response from a peer.
 */
public class ResponseFormatException extends Exception {
    public ResponseFormatException(String message) {
        super("Response format invalid: " + message);
    }
}
