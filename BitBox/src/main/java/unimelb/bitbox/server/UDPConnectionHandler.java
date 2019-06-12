package unimelb.bitbox.server;

import unimelb.bitbox.messages.ConnectionRefused;
import unimelb.bitbox.messages.HandshakeRequest;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.peers.PeerType;
import unimelb.bitbox.peers.PeerUDP;
import unimelb.bitbox.util.functional.algebraic.Maybe;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.UDPSocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

class UDPConnectionHandler extends ConnectionHandler {
    private static final int UDP_MAX_PACKET = 65507;

    @Override
    void acceptConnections() throws IOException {
        // Maximum packet size is 65507 bytes
        byte[] buffer = new byte[UDP_MAX_PACKET];

        setSocket(new UDPSocket(port, 100));
        DatagramSocket udpSocket = awaitUDPSocket();

        PeerServer.log().fine("Listening on port " + this.port);
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
                                Peer peer = new PeerUDP(name, PeerType.INCOMING, udpSocket, packet);
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
                    Message message = new ConnectionRefused("connection list full");
                    byte[] responseBuffer = message.networkEncode().getBytes(StandardCharsets.UTF_8);
                    packet.setData(responseBuffer);
                    packet.setLength(responseBuffer.length);
                    udpSocket.send(packet);
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                PeerServer.log().severe("Failed receiving from peer: " + e.getMessage());
                e.printStackTrace();
            }
        }
        PeerServer.log().fine("No longer listening on port " + port);
    }

    @Override
    Maybe<Peer> tryPeer(HostPort peerHostPort) {
        if (hasPeer(peerHostPort)) {
            return Maybe.nothing();
        }
        addPeerAddress(peerHostPort);


        String name = getAnyName();

        byte[] buffer = new byte[UDP_MAX_PACKET];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, new InetSocketAddress(peerHostPort.hostname, peerHostPort.port));
        Peer peer = new PeerUDP(name, PeerType.OUTGOING, awaitUDPSocket(), packet);
        peer.sendMessage(new HandshakeRequest());

        addPeer(peer);
        return Maybe.just(peer);
    }
}
