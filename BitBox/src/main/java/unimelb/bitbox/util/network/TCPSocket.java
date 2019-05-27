package unimelb.bitbox.util.network;

import java.io.IOException;
import java.net.ServerSocket;

public class TCPSocket extends SocketWrapper {
    private ServerSocket socket;

    public TCPSocket(int port) throws IOException {
        socket = new ServerSocket(port);
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
