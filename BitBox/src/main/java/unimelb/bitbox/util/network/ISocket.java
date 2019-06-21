package unimelb.bitbox.util.network;

import java.io.IOException;

/**
 * Represents a Socket that can be closed (possibly failing), and can be queried to check if it is open.
 *
 * @author Eleanor McMurtry
 */
public interface ISocket {
    void close() throws IOException;
    boolean isClosed();
}