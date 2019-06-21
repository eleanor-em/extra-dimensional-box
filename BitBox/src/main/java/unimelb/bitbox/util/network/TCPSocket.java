package unimelb.bitbox.util.network;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * A TCP implementation of {@link ISocket}.
 */
public class TCPSocket implements ISocket {
    private final ServerSocket socket;

    /**
     * Initialises the socket with infinite timeout.
     */
    public TCPSocket(int port) throws IOException {
        this(port, 0);
    }

    /**
     * This constructor indicates how long a new connection should wait before considered a failure.
     */
    public TCPSocket(int port, int timeout) throws IOException {
        socket = new ServerSocket(port);
        socket.setSoTimeout(timeout);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    @Override
    public boolean isClosed() {
        return socket.isClosed();
    }

    public ServerSocket get() {
        return socket;
    }
}
