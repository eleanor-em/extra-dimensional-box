package unimelb.bitbox.peers;

import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.util.config.CfgValue;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerUDP extends PeerConnection {
    private HashMap<String, RetryThread> retryThreads;
    private HashMap<String, RetryThread> getRetryThreads() {
        if (retryThreads == null) {
            retryThreads = new HashMap<>();
        }
        return retryThreads;
    }

    // EXTENSION: Allow sockets with the wrong port
    /*@Override
    void activateDefault(String host, long port) {
        synchronized (this) {
            if (state != PeerState.CLOSED && state != PeerState.INACTIVE) {
                // UDP peer already has accurate host and port
                state = PeerState.ACTIVE;
                KnownPeerTracker.addAddress(getHost() + ":" + getPort());
            }
        }
    }*/

    public PeerUDP(String name, ServerMain server, boolean outgoing, DatagramSocket socket, DatagramPacket packet) {
        super(name, server, outgoing, packet.getAddress().toString().split("/")[1],
              packet.getPort(), new OutgoingConnectionUDP(socket, packet));
    }


    // Override to not close the socket, as it's shared between all peers.
    @Override
    public void close() {
        synchronized (this) {
            if (state == PeerState.CLOSED) {
                return;
            }
            ServerMain.log.warning("Connection to peer `" + getForeignName() + "` closed.");
            state = PeerState.CLOSED;
            server.closeConnection(this);

            outConn.deactivate();
            getRetryThreads().forEach((ignored, thread) -> thread.kill());
        }
    }

    public void retryMessage(Message message) {
        sendMessageInternal(message);
    }

    @Override
    protected void sendMessageInternal(Message message, Runnable onSent) {
        if (message.isRequest() && !getRetryThreads().containsKey(message.getSummary())) {
            ServerMain.log.info(getForeignName() + ": waiting for response: " + message.getSummary());
            RetryThread thread = new RetryThread(this, message);
            getRetryThreads().put(message.getSummary(), thread);
            thread.start();
        }
        super.sendMessageInternal(message, onSent);
    }

    @Override
    public void notify(Message message) {
        if (!message.isRequest()) {
            ServerMain.log.info(getForeignName() + ": notified response: " + message.getSummary());
            Optional.ofNullable(getRetryThreads().get(message.getSummary()))
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
                    ServerMain.log.info(parent.getForeignName() + ": resending " + message.getCommand() + " (" + retries + ")");
                    parent.retryMessage(message);

                    synchronized (this) {
                        ++retries;
                    }
                }
            }
            if (alive.get()) {
                ServerMain.log.warning(parent.getForeignName() + ": timed out: " + message.getCommand());
            }
            parent.close();
        }
    }
}
