package unimelb.bitbox.peers;

import unimelb.bitbox.server.PeerServer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents an outgoing connection to a peer.
 *
 * @author Eleanor McMurtry
 */
class OutgoingConnection implements Runnable {
    private final BlockingQueue<OutgoingMessage> messages = new LinkedBlockingQueue<>();
    private final Socket socket;

    OutgoingConnection(Socket socket) {
        this.socket = socket;
    }

    final void addMessage(OutgoingMessage message) {
        messages.add(message);
    }
    private OutgoingMessage takeMessage() throws InterruptedException {
        return messages.take();
    }

    public void run() {
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            while (!socket.isClosed()) {
                OutgoingMessage message = takeMessage();
                out.write(message.message);
                out.flush();
                message.onSent.run();
            }
        } catch (IOException e) {
            PeerServer.log().severe("Error writing to socket: " + e.getMessage());
        } catch (InterruptedException e) {
            PeerServer.log().fine("thread interrupted: " + e.getMessage());
        }
    }
}