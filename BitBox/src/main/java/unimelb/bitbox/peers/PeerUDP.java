package unimelb.bitbox.peers;

import unimelb.bitbox.messages.Message;
import unimelb.bitbox.messages.MessageType;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.concurrency.KeepAlive;
import unimelb.bitbox.util.config.CfgValue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class PeerUDP extends Peer {
    private final RetryService.RetryServer retryServer = new RetryService.RetryServer(this);

    public PeerUDP(String name, PeerType type, DatagramSocket socket, DatagramPacket packet) {
        super(name, type, packet.getAddress().toString().split("/")[1], packet.getPort(),
              new OutgoingConnectionUDP(socket, packet));
    }

    @Override
    protected void closeInternal() {
        retryServer.cancel();
    }

    @Override
    protected void requestSent(Message request) {
        retryServer.submit(request);
    }

    @Override
    protected void responseReceived(Message response) {
        retryServer.notify(response);
    }
}

class OutgoingConnectionUDP extends OutgoingConnection {
    private final DatagramSocket udpSocket;
    private final DatagramPacket packet;

    OutgoingConnectionUDP(DatagramSocket socket, DatagramPacket packet) {
        udpSocket = socket;
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
                PeerServer.log().severe("Error sending packet to UDP socket: " + e.getMessage());
            } catch (InterruptedException e) {
                PeerServer.log().fine("thread interrupted: " + e.getMessage());
            }
        }
    }
}

class RetryService {
    private RetryService() {}

    public static class RetryServer {
        protected final PeerUDP peer;

        RetryServer(PeerUDP peer) {
            this.peer = peer;
        }

        void submit(Message request) {
            RequestData req = new RequestData(peer, request);
            if (trackedRequests.add(req)) {
                retries.add(new RetryInstance(req));
            }
        }
        void notify(Message response) {
            trackedRequests.remove(new RequestData(peer, response));
        }

        void cancel() {
            // This weird construction is required due to a clumsy implementation of ConcurrentHashMap.entrySet().removeIf()
            Iterator<RequestData> it = trackedRequests.iterator();
            //noinspection WhileLoopReplaceableByForEach
            while (it.hasNext()) {
                RequestData req = it.next();
                if (req.peer.equals(peer)) {
                    trackedRequests.remove(req);
                }
            }
        }
    }

    private static class RequestData {
        public final PeerUDP peer;
        final String digest;
        public final Message request;

        RequestData(PeerUDP peer, Message request) {
            this.peer = peer;
            digest = request.getSummary();
            this.request = request;
        }

        @Override
        public int hashCode() {
            return (digest + peer).hashCode();
        }

        @Override
        public boolean equals(Object rhs) {
            if (rhs instanceof RequestData) {
                RequestData other = (RequestData) rhs;
                return peer.equals(other.peer) && digest.equals(other.digest);
            }
            return false;
        }
    }

    private static class RetryInstance {
        private static final CfgValue<Integer> retryCount = CfgValue.createInt("udpRetries");
        private static final CfgValue<Integer> retryTime = CfgValue.createInt("udpTimeout");

        public final RequestData data;
        private long submissionTime;
        private int retries = 0;

        boolean retry() {
            if (data.peer.isClosed()) {
                return false;
            }

            data.peer.sendMessage(data.request);
            submissionTime = System.nanoTime();
            if (retries >= retryCount.get()) {
                PeerServer.log().warning("peer " + data.peer + " timed out");
                data.peer.close();
                return false;
            } else {
                final String command = data.request.getCommand().map(MessageType::toString).orElse("<UNKNOWN>");
                PeerServer.log().fine(data.peer + ": retrying " + command + "(" + retries + ")");
                ++retries;
                return true;
            }
        }

        long timeToWait() {
            //noinspection MagicNumber: 1000000 is the obvious conversion from nanoseconds to milliseconds
            return retryTime.get() + (submissionTime - System.nanoTime()) / 1000000;
        }

        private RetryInstance(RequestData data) {
            submissionTime = System.nanoTime();
            this.data = data;
        }
    }

    private static final BlockingQueue<RetryInstance> retries = new LinkedBlockingQueue<>();
    private static final Set<RequestData> trackedRequests = ConcurrentHashMap.newKeySet();

    static {
        KeepAlive.submit(RetryService::run);
    }

    private static void run() {
        while (true) {
            try {
                RetryInstance request = retries.take();

                // Sleep just a tiny bit less than we need to.
                long delta = request.timeToWait();
                if (delta > 0) {
                    Thread.sleep(delta);
                }

                if (trackedRequests.contains(request.data)) {
                    if (request.retry()) {
                        retries.add(request);
                    }
                }
            } catch (InterruptedException ignored) {
                PeerServer.log().warning("Retry service interrupted");
            }
        }
    }
}