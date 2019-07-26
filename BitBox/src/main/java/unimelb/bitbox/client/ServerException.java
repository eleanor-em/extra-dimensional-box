package unimelb.bitbox.client;

/**
 * @author Eleanor McMurtry
 */
class ServerException extends Exception {
    ServerException(String message) {
        super(message);
    }
    ServerException(Throwable t) {
        super(t);
    }
}
