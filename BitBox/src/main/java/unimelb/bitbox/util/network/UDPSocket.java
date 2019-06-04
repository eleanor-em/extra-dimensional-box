package unimelb.bitbox.util.network;

import java.io.IOException;
import java.net.DatagramSocket;

public class UDPSocket implements ISocket {
    private DatagramSocket socket;

    public UDPSocket(int port) throws IOException {
        this(port, 0);
    }
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
