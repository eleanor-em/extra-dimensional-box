package unimelb.bitbox.util.network;

import java.io.IOException;

public interface ISocket {
    void close() throws IOException;
    boolean isClosed();
}

