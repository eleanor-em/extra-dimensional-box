package unimelb.bitbox.peers;

import unimelb.bitbox.messages.Message;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.concurrency.ConcurrentLinkedSet;
import unimelb.bitbox.util.concurrency.KeepAlive;
import unimelb.bitbox.util.config.CfgValue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * A UDP implementation of the {@link Peer}.
 *
 * @author Eleanor McMurtry
 * @author Benjamin(Jingyi Li) Li
 */
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
        while (!udpSocket.isClosed()) {
            try {
                OutgoingMessage message = takeMessage();
                byte[] buffer = message.networkEncoded().getBytes(StandardCharsets.UTF_8);
                packet.setData(buffer);
                packet.setLength(buffer.length);
                udpSocket.send(packet);
                message.onSent.run();
            } catch (IOException e) {
                PeerServer.log().severe("Error sending packet to UDP socket: ".toString() + e.getMessage());
            } catch (InterruptedException e) {
                PeerServer.log().fine("thread interrupted: " + e.getMessage());
            }
        }
    }
}

class RetryService {
    private static final ConcurrentLinkedSet<RequestData> requests = new ConcurrentLinkedSet<>();
    private RetryService() {}

    public static class RetryServer {
        final PeerUDP peer;

        RetryServer(PeerUDP peer) {
            this.peer = peer;
        }

        void submit(Message request) {
            if (requests.add(new RequestData(peer, request))) {
                PeerServer.log().fine("tracking response for " + request.getSummary());
            }
        }

        void notify(Message response) {
            if (requests.remove(new RequestData(peer, response))) {
                PeerServer.log().fine("notified response for " + response.getSummary());
            }
        }

        void cancel() {
            requests.removeIf(req -> req.peer == peer);
        }
    }

    private static class RequestData {
        private static final CfgValue<Integer> retryCount = CfgValue.createInt("udpRetries");
        private static final CfgValue<Integer> retryTime = CfgValue.createInt("udpTimeout");

        private final PeerUDP peer;
        private final String digest;
        private final Message request;

        private final long submissionTime;
        private final int retries;

        RequestData(PeerUDP peer, Message request) {
            assert request.isRequest();

            this.peer = peer;
            digest = request.getSummary();
            this.request = request;

            submissionTime = System.nanoTime();
            retries = 0;
        }

        private RequestData(RequestData old, int retries) {
            peer = old.peer;
            digest = old.digest;
            request = old.request;

            submissionTime = System.nanoTime();
            this.retries = retries;
        }

        void retry() {
            if (!peer.isClosed()) {
                if (retries == retryCount.get()) {
                    PeerServer.log().warning("peer " + peer + " timed out");
                    peer.close();
                } else {
                    PeerServer.log().fine(peer + ": retrying " + request.getSummary() + " (" + retries + ")");
                    //PeerServer.log().info("retrying " + request.getSummary() + " (" + retries + ")");
                    // Re-send the request. Add another tracker first so we don't double up
                    assert requests.add(new RequestData(this, retries + 1));
                    peer.sendMessage(request);
                }
            }
        }

        void await() throws InterruptedException {
            // If we're too early, then sleep until we're ready
            long delta = retryTime.get() + (submissionTime - System.nanoTime()) / 1000000;
            if (delta > 0) {
                Thread.sleep(delta);
            }
        }
        @Override
        public int hashCode() {
            return digest.hashCode();
        }

        @Override
        public boolean equals(Object rhs) {
            return rhs instanceof RequestData && digest.equals(((RequestData) rhs).digest);
        }
    }

    static {
        KeepAlive.submit(RetryService::run);
    }

    private static void run() {
        while (true) {
            try {
                RequestData request = requests.take();
                request.await();
                request.retry();
            } catch (InterruptedException ignored) {
                PeerServer.log().warning("retry service interrupted");
            }
        }
    }
}