package unimelb.bitbox.server.connections;

import unimelb.bitbox.messages.ConnectionRefused;
import unimelb.bitbox.messages.HandshakeRequest;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.peers.PeerTCP;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.functional.algebraic.Maybe;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.TCPSocket;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class TCPConnectionHandler extends ConnectionHandler {
    @Override
    void acceptConnections() throws IOException {
        // Need to set and then await in case there was already a socket created
        setSocket(new TCPSocket(this.port, 100));
        ServerSocket tcpServerSocket = awaitTCPSocket();
        PeerServer.log().info("Listening on port " + this.port);

        while (!tcpServerSocket.isClosed()) {
            try {
                Socket socket = tcpServerSocket.accept();
                PeerServer.log().info("Accepted connection: " + socket.getInetAddress().toString() + ":" + socket.getPort());

                // check we have room for more peers
                // (only count incoming connections)
                if (canStorePeer()) {
                    Peer peer = new PeerTCP(getAnyName(), socket, false);
                    addPeer(peer);
                    PeerServer.log().info("Connected to peer " + peer);
                } else {
                    // if not, write a CONNECTION_REFUSED message and close the connection
                    try (BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                        out.write(new ConnectionRefused(getActivePeers()).networkEncode());
                        out.flush();
                        PeerServer.log().info("Sending CONNECTION_REFUSED");
                    } catch (IOException e) {
                        e.printStackTrace();
                        PeerServer.log().warning("Failed writing CONNECTION_REFUSED");
                    } finally {
                        socket.close();
                    }
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                PeerServer.log().warning("Failed connecting to peer");
                e.printStackTrace();
            }
        }
        PeerServer.log().info("No longer listening on port " + this.port);
    }

    @Override
    Maybe<Peer> tryPeer(HostPort peerHostPort) {
        if (hasPeer(peerHostPort)) {
            return Maybe.nothing();
        }
        addPeerAddress(peerHostPort);

        try {
            Socket socket = new Socket(peerHostPort.hostname, peerHostPort.port);

            // find a name
            String name = getAnyName();
            Peer peer = new PeerTCP(name, socket, true);
            addPeer(peer);

            // send a handshake to the peer
            PeerServer.log().info(peer.getForeignName() + ": Sending handshake request");
            peer.sendMessage(new HandshakeRequest());

            // success: remove this peer from the set of peers to connect to
            PeerServer.log().info("Connected to peer " + name + " @ " + peerHostPort);
            return Maybe.just(peer);
        } catch (IOException e) {
            PeerServer.log().warning("Connection to peer `" + peerHostPort + "` failed: " + e.getMessage());
            e.printStackTrace();
            return Maybe.nothing();
        }
    }
}
