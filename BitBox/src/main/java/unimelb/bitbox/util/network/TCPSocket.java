package unimelb.bitbox.util.network;

import java.io.IOException;
import java.net.ServerSocket;

public class TCPSocket implements ISocket {
    private final ServerSocket socket;
    public TCPSocket(int port) throws IOException {
        this(port, 0);
    }
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
