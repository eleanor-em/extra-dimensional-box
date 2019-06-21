package unimelb.bitbox.util.network;

import java.io.IOException;
import java.net.DatagramSocket;

/**
 * A UDP implementation of {@link ISocket}.
 *
 * @author Eleanor McMurtry
 */
public class UDPSocket implements ISocket {
    private final DatagramSocket socket;

    /**
     * Initialises the socket with infinite timeout.
     */
    public UDPSocket(int port) throws IOException {
        this(port, 0);
    }

    /**
     * This constructor indicates how long a new connection should wait before considered a failure.
     */
    public UDPSocket(int port, int timeout) throws IOException {
        socket = new DatagramSocket(port);
        socket.setSoTimeout(timeout);
    }

    @Override
    public void close() {
        socket.close();
    }

    @Override
    public boolean isClosed() {
        return socket.isClosed();
    }

    public DatagramSocket get() {
        return socket;
    }
}
