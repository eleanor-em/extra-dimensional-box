package unimelb.bitbox.server.connections;

import unimelb.bitbox.messages.ConnectionRefused;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.peers.PeerUDP;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.functional.algebraic.Maybe;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.UDPSocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class UDPConnectionHandler extends ConnectionHandler {
    public UDPConnectionHandler(PeerServer server) {
        super(server);
    }

    @Override
    void acceptConnections() throws IOException {
        // Maximum packet size is 65507 bytes
        byte[] buffer = new byte[65507];

        setSocket(new UDPSocket(port, 100));
        DatagramSocket udpSocket = awaitUDPSocket();

        PeerServer.log.info("Listening on port " + this.port);
        while (!udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                HostPort hostPort = new HostPort(packet.getAddress().toString(), packet.getPort());

                String name = getAnyName();
                // Look up the peer. If we don't find an existing one, try to create a new one.
                Maybe<Peer> connectedPeer = getPeer(hostPort).matchThen(
                        Maybe::just,
                        () -> {
                            if (canStorePeer()) {
                                // Create the peer if we have room for another
                                Peer peer = new PeerUDP(name, server, false, udpSocket, packet);
                                addPeer(peer);
                                return Maybe.just(peer);
                            }
                            return Maybe.nothing();
                        });

                // If we ended up with a peer, receive the message.
                if (connectedPeer.isJust()) {
                    // The actual message may be shorter than what we got from the socketContainer
                    String packetData = new String(packet.getData(), 0, packet.getLength());
                    connectedPeer.get().receiveMessage(packetData);
                } else {
                    // Otherwise, send CONNECTION_REFUSED
                    Message message = new ConnectionRefused(getActivePeers());
                    byte[] responseBuffer = message.networkEncode().getBytes(StandardCharsets.UTF_8);
                    packet.setData(responseBuffer);
                    packet.setLength(responseBuffer.length);
                    udpSocket.send(packet);
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                PeerServer.log.severe("Failed receiving from peer: " + e.getMessage());
                e.printStackTrace();
            }
        }
        PeerServer.log.info("No longer listening on port " + this.port);
    }

    @Override
    Maybe<Peer> tryPeer(HostPort peerHostPort) {
        if (hasPeer(peerHostPort)) {
            return Maybe.nothing();
        }
        addPeerAddress(peerHostPort);

        // Have to use AtomicReference because Java isn't smart enough to realise Peer is final
        AtomicReference<Peer> peer = new AtomicReference<>();


        String name = getAnyName();

        byte[] buffer = new byte[65507];
        //send handshake request
        DatagramPacket packet = new DatagramPacket(buffer,
                buffer.length,
                new InetSocketAddress(peerHostPort.hostname, peerHostPort.port));
        Peer newPeer = new PeerUDP(name, server, true, awaitUDPSocket(), packet);
        addPeer(newPeer);
        PeerServer.log.info("Attempting to send handshake to " + newPeer + ", waiting for response;");

        peer.set(newPeer);
        return Maybe.just(peer.get());
    }
}
