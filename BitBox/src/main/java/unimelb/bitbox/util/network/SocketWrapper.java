package unimelb.bitbox.util.network;

import java.io.IOException;

public abstract class SocketWrapper {
    public abstract void close() throws IOException;
    public abstract boolean isClosed();
}

