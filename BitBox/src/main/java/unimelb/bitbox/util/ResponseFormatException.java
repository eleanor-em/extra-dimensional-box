package unimelb.bitbox.util;


import org.json.simple.parser.ParseException;

/**
 * Thrown in case of a malformed response from a peer.
 */
public class ResponseFormatException extends Exception {
    private ParseException cause;

    @Override
    public void printStackTrace() {
        super.printStackTrace();
        if (cause != null) {
            System.out.println("caused by:");
            cause.printStackTrace();
        }
    }

    public ResponseFormatException(String message) {
        super("Response format invalid: " + message);
    }
    public ResponseFormatException(ParseException cause) {
        super("Error parsing JSON: " + cause);
        this.cause = cause;
    }
}
