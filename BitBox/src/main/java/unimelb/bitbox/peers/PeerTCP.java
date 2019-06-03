package unimelb.bitbox.peers;

import unimelb.bitbox.server.PeerServer;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class PeerTCP extends Peer {
    private Socket socket;

    public PeerTCP(String name, Socket socket, boolean outgoing) {
        super(name, outgoing, socket.getInetAddress().getHostAddress(), socket.getPort(), new OutgoingConnectionTCP(socket));
        this.submit(this::receiveMessages);
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

    private void receiveMessages() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while ((message = in.readLine()) != null) {
                PeerTCP.this.receiveMessage(message);
            }
        } catch (IOException e) {
            if (!PeerTCP.this.isClosed()) {
                PeerServer.logSevere("Error reading from socket: " + e.getMessage());
                PeerTCP.this.close();
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
