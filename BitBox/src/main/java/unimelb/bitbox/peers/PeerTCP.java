package unimelb.bitbox.peers;

import unimelb.bitbox.server.PeerServer;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class PeerTCP extends Peer {
    private Socket socket;

    public PeerTCP(String name, Socket socket, boolean outgoing) {
        super(name, outgoing, socket.getInetAddress().getHostAddress(), socket.getPort(), new OutgoingConnectionTCP(socket));
        IncomingConnectionTCP inConn = new IncomingConnectionTCP(socket, this);
        inConn.start();

        this.socket = socket;
    }

    @Override
    protected void closeInternal() {
        try {
            socket.close();
        } catch (IOException e) {
            PeerServer.logSevere("Error closing socket: " + e.getMessage());
        }
    }
}

class IncomingConnectionTCP extends Thread {
    private Socket socket;
    // the Peer object that will forward our received messages
    private Peer consumer;

    IncomingConnectionTCP(Socket socket, Peer consumer) {
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
            if (!consumer.isClosed()) {
                PeerServer.logSevere("Error reading from socket: " + e.getMessage());
                consumer.close();
            }
        }
    }
}

class OutgoingConnectionTCP extends OutgoingConnection {
    private Socket socket;

    OutgoingConnectionTCP(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            while (!socket.isClosed() && isActive()) {
                OutgoingMessage message = takeMessage();
                out.write(message.message);
                out.flush();
                message.onSent.run();
            }
        } catch (IOException e) {
            PeerServer.logSevere("Error writing to socket: " + e.getMessage());
        } catch (InterruptedException e) {
            PeerServer.logInfo("thread interrupted: " + e.getMessage());
        }
    }
}
