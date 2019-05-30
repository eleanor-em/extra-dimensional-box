package unimelb.bitbox.client.requests;

/**
 * An exception that can be thrown while parsing the arguments for the client.
 */
public class ClientArgsException extends Exception {
    public ClientArgsException(String cause) {
        super(cause);
    }
    public ClientArgsException(Exception cause) {
        super(cause);
    }
}
