package unimelb.bitbox.peers;

import unimelb.bitbox.server.ServerMain;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class OutgoingConnection extends Thread {
    private AtomicBoolean active = new AtomicBoolean(true);
    private final BlockingQueue<OutgoingMessage> messages = new LinkedBlockingQueue<>();
    void deactivate() {
        active.set(false);
    }

    boolean isActive() {
        return active.get();
    }

    protected void addMessage(OutgoingMessage message) {
        messages.add(message);
    }
    protected OutgoingMessage takeMessage() throws InterruptedException {
        return messages.take();
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
                out.write(message.message + "\n");
                out.flush();
                message.onSent.run();
            }
        } catch (IOException | InterruptedException e) {
            ServerMain.log.severe("Error writing to socket: " + e.getMessage());
        }
    }
}
class OutgoingConnectionUDP extends OutgoingConnection {
    private DatagramSocket udpSocket;
    private DatagramPacket packet;

    OutgoingConnectionUDP(DatagramSocket socket, DatagramPacket packet) {
        this.udpSocket = socket;
        this.packet = packet;
    }

    @Override
    public void run() {
        ServerMain.log.info("Outgoing thread starting");
        while (!udpSocket.isClosed() && isActive()) {
            try {
                OutgoingMessage message = takeMessage();
                byte[] buffer = (message.message + "\n").getBytes(StandardCharsets.UTF_8);
                packet.setData(buffer);
                packet.setLength(buffer.length);
                udpSocket.send(packet);
                message.onSent.run();
            } catch (IOException | InterruptedException | NullPointerException e) {
                ServerMain.log.severe("Error sending packet to UDP socket: " + e.getMessage());
            }
        }
        ServerMain.log.warning("Outgoing thread exiting");
    }
}