package unimelb.bitbox.peers;

import unimelb.bitbox.server.ServerMain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class PeerTCP extends PeerConnection {
    private Socket socket;

    public PeerTCP(String name, Socket socket, ServerMain server, boolean outgoing) {
        super(name, server, outgoing, socket.getInetAddress().getHostAddress(), socket.getPort(), new OutgoingConnectionTCP(socket));
        IncomingConnectionTCP inConn = new IncomingConnectionTCP(socket, this);
        inConn.start();

        this.socket = socket;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (state == PeerState.CLOSED) {
                return;
            }
            ServerMain.log.warning("Connection to peer `" + getForeignName() + "` closed.");

            try {
                socket.close();
            } catch (IOException e) {
                ServerMain.log.severe("Error closing socket: " + e.getMessage());
            }
            state = PeerState.CLOSED;

            outConn.deactivate();
            server.closeConnection(this);
        }
    }
}

class IncomingConnectionTCP extends Thread {
    private Socket socket;
    // the PeerConnection object that will forward our received messages
    private PeerConnection consumer;

    IncomingConnectionTCP(Socket socket, PeerConnection consumer) {
        this.socket = socket;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while ((message = in.readLine()) != null) {
                consumer.receiveMessage(message);
            }
        } catch (IOException e) {
            if (consumer.getState() != PeerConnection.PeerState.CLOSED) {
                ServerMain.log.severe("Error reading from socket: " + e.getMessage());
                consumer.close();
            }
        }
    }
}
