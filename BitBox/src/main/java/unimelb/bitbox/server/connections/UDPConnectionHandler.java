package unimelb.bitbox.server.connections;

import unimelb.bitbox.messages.ConnectionRefused;
import unimelb.bitbox.messages.Message;
import unimelb.bitbox.peers.PeerConnection;
import unimelb.bitbox.peers.PeerUDP;
import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.UDPSocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class UDPConnectionHandler extends ConnectionHandler {
    public UDPConnectionHandler(ServerMain server) {
        super(server);
    }

    @Override
    void acceptConnections() throws IOException {
        // Maximum packet size is 65507 bytes
        byte[] buffer = new byte[65507];

        setSocket(new UDPSocket(port, 100));
        Optional<DatagramSocket> maybeSocket = awaitUDPSocket();
        if (!maybeSocket.isPresent()) {
            return;
        }

        DatagramSocket udpSocket = maybeSocket.get();

        ServerMain.log.info("Listening on port " + this.port);
        while (!udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                HostPort hostPort = new HostPort(packet.getAddress().toString(), packet.getPort());

                String name = getAnyName();
                PeerConnection connectedPeer = getPeer(hostPort);

                // Check if this is a new peer
                if (connectedPeer == null) {
                    if (canStorePeer()) {
                        // Create the peer if we have room for another
                        connectedPeer = new PeerUDP(name, server, false, udpSocket, packet);
                        addPeer(connectedPeer);
                    } else {
                        // Send CONNECTION_REFUSED
                        Message message = new ConnectionRefused(getActivePeers());
                        byte[] responseBuffer = message.networkEncode().getBytes(StandardCharsets.UTF_8);
                        packet.setData(responseBuffer);
                        packet.setLength(responseBuffer.length);
                        udpSocket.send(packet);
                        continue;
                    }
                }
                // The actual message may be shorter than what we got from the socketContainer
                String packetData = new String(packet.getData(), 0, packet.getLength());
                connectedPeer.receiveMessage(packetData);
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                ServerMain.log.severe("Failed receiving from peer: " + e.getMessage());
            }
        }
        ServerMain.log.info("No longer listening on port " + this.port);
    }

    @Override
    PeerConnection tryPeer(HostPort peerHostPort) {
        if (hasPeer(peerHostPort)) {
            return null;
        }
        addPeerAddress(peerHostPort);

        AtomicReference<PeerConnection> peer = new AtomicReference<>();
        awaitUDPSocket().ifPresent(socket -> {
            String name = getAnyName();

            byte[] buffer = new byte[65507];
            //send handshake request
            DatagramPacket packet = new DatagramPacket(buffer,
                    buffer.length,
                    new InetSocketAddress(peerHostPort.hostname, peerHostPort.port));
            PeerConnection newPeer = new PeerUDP(name, server, true, socket, packet);
            addPeer(newPeer);
            ServerMain.log.info("Attempting to send handshake to " + newPeer + ", waiting for response;");

            peer.set(newPeer);
        });
        return peer.get();
    }
}
