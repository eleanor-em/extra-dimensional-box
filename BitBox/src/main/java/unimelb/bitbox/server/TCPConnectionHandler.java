package unimelb.bitbox.server;

import functional.algebraic.Maybe;
import unimelb.bitbox.messages.ConnectionRefused;
import unimelb.bitbox.messages.HandshakeRequest;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.peers.PeerTCP;
import unimelb.bitbox.peers.PeerType;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.TCPSocket;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * TCP implementation of {@link ConnectionHandler}.
 *
 * @author Eleanor McMurtry
 */
class TCPConnectionHandler extends ConnectionHandler {
    @Override
    void acceptConnections() throws IOException {
        // Need to set and then await in case there was already a socket created
        setSocket(new TCPSocket(port, 100));
        final ServerSocket tcpServerSocket = awaitTCPSocket();
        PeerServer.log().fine("Listening on port " + port);

        while (!tcpServerSocket.isClosed()) {
            try {
                Socket socket = tcpServerSocket.accept();
                PeerServer.log().fine("Accepted connection: " + socket.getInetAddress() + ":" + socket.getPort());

                // check we have room for more peers
                // (only count incoming connections)
                if (canStorePeer()) {
                    final Peer peer = new PeerTCP(getAnyName(), socket, PeerType.INCOMING);
                    addPeer(peer);
                    PeerServer.log().fine("Connected to peer " + peer);
                } else {
                    // if not, write a CONNECTION_REFUSED message and close the connection
                    try (BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                        out.write(new ConnectionRefused("connection list full").networkEncode());
                        out.flush();
                        PeerServer.log().fine("Sending CONNECTION_REFUSED");
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
        PeerServer.log().fine("No longer listening on port " + this.port);
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
            Peer peer = new PeerTCP(name, socket, PeerType.OUTGOING);
            peer.sendMessage(new HandshakeRequest());
            addPeer(peer);

            try {
                if (peer.awaitActivation()) {
                    PeerServer.log().fine("Connected to peer " + name + " @ " + peerHostPort);
                    return Maybe.just(peer);
                } else {
                    PeerServer.log().fine("Failed to connect to peer " + name + " @ " + peerHostPort);
                }
            } catch (InterruptedException ignored) {}
        } catch (IOException e) {
            PeerServer.log().warning("Connection to peer `" + peerHostPort + "` failed: " + e.getMessage());
        }

        return Maybe.nothing();
    }
}
