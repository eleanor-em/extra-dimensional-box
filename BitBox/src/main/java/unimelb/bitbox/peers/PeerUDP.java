package unimelb.bitbox.peers;

import unimelb.bitbox.messages.Message;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.concurrency.LazyInitialiser;
import unimelb.bitbox.util.config.CfgValue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerUDP extends Peer {
    private final LazyInitialiser<HashMap<String, RetryThread>> retryThreads = new LazyInitialiser<>(HashMap::new);

    public PeerUDP(String name, PeerServer server, boolean outgoing, DatagramSocket socket, DatagramPacket packet) {
        super(name, server, outgoing, packet.getAddress().toString().split("/")[1],
              packet.getPort(), new OutgoingConnectionUDP(socket, packet));
    }


    // Override to not close the socket, as it's shared between all peers.
    @Override
    protected void closeInternal() {
        retryThreads.get().forEach((ignored, thread) -> thread.kill());
    }

    private void retryMessage(Message message) {
        sendMessageInternal(message);
    }

    @Override
    protected void sendMessageInternal(Message message, Runnable onSent) {
        if (message.isRequest() && !retryThreads.get().containsKey(message.getSummary())) {
            PeerServer.log.info(getForeignName() + ": waiting for response: " + message.getSummary());
            RetryThread thread = new RetryThread(this, message);
            retryThreads.get().put(message.getSummary(), thread);
            thread.start();
        }
        super.sendMessageInternal(message, onSent);
    }

    @Override
    protected void notifyInternal(Message message) {
        if (!message.isRequest()) {
            PeerServer.log.info(getForeignName() + ": notified response: " + message.getSummary());
            Optional.ofNullable(retryThreads.get().get(message.getSummary()))
                    .ifPresent(Thread::interrupt);
        }
    }

    private class RetryThread extends Thread {
        private Message message;
        private PeerUDP parent;
        private CfgValue<Integer> retryCount = CfgValue.createInt("udpRetries");
        private CfgValue<Integer> retryTime = CfgValue.createInt("udpTimeout");
        private int retries = 0;
        private AtomicBoolean alive = new AtomicBoolean(true);

        public RetryThread(PeerUDP parent, Message message) {
            this.parent = parent;
            this.message = message;
        }

        private boolean shouldRetry() {
            synchronized (this) {
                return alive.get() && retries < retryCount.get();
            }
        }

        public void kill() {
            synchronized (this) {
                alive.set(false);
                interrupt();
            }
        }

        @Override
        public void run() {
            while (shouldRetry()) {
                try {
                    Thread.sleep(retryTime.get());
                } catch (InterruptedException e) {
                    return;
                }
                if (alive.get()) {
                    PeerServer.log.info(parent.getForeignName() + ": resending " + message.getCommand() + " (" + retries + ")");
                    parent.retryMessage(message);

                    synchronized (this) {
                        ++retries;
                    }
                }
            }
            if (alive.get()) {
                PeerServer.log.warning(parent.getForeignName() + ": timed out: " + message.getCommand());
            }
            parent.close();
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
        while (!udpSocket.isClosed() && isActive()) {
            try {
                OutgoingMessage message = takeMessage();
                byte[] buffer = message.networkEncoded().getBytes(StandardCharsets.UTF_8);
                packet.setData(buffer);
                packet.setLength(buffer.length);
                udpSocket.send(packet);
                message.onSent.run();
            } catch (IOException e) {
                PeerServer.log.severe("Error sending packet to UDP socket: " + e.getMessage());
            } catch (InterruptedException e) {
                PeerServer.log.info("thread interrupted: " + e.getMessage());
            }
        }
    }
}