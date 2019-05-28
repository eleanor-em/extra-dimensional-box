package unimelb.bitbox.client.requests;

public class ClientArgsException extends Exception {
    public ClientArgsException(String cause) {
        super(cause);
    }
    public ClientArgsException(Exception cause) {
        super(cause);
    }
}
