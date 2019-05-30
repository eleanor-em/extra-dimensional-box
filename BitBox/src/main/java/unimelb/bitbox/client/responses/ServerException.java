package unimelb.bitbox.client.responses;

/**
 * An exception that can occur while generating a response.
 */
public class ServerException extends Exception {
    public ServerException(Exception cause) { super(cause); }
    public ServerException(String cause) { super(cause); }
}
