package unimelb.bitbox.client.responses;

public class ServerException extends Exception {
    public ServerException(Exception cause) { super(cause); }
    public ServerException(String cause) { super(cause); }
}
