package unimelb.bitbox.server.connections;

import unimelb.bitbox.messages.ConnectionRefused;
import unimelb.bitbox.peers.PeerConnection;
import unimelb.bitbox.peers.PeerTCP;
import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.TCPSocket;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class TCPConnectionHandler extends ConnectionHandler {
    public TCPConnectionHandler(ServerMain server) {
        super(server);
    }

    @Override
    void acceptConnections() throws IOException {
        setSocket(new TCPSocket(port));
        Optional<ServerSocket> maybeSocket = getSocketAsTCP();
        if (!maybeSocket.isPresent()) {
            return;
        }

        ServerSocket tcpServerSocket = maybeSocket.get();
        while (!tcpServerSocket.isClosed()) {
            try {
                Socket socket = tcpServerSocket.accept();

                // check we have room for more peers
                // (only count incoming connections)
                if (canStorePeer()) {
                    PeerConnection peer = new PeerTCP(getAnyName(), socket, server, false);
                    addPeer(peer);
                    ServerMain.log.info("Connected to peer " + peer);
                } else {
                    // if not, write a CONNECTION_REFUSED message and close the connection
                    try (BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                        out.write(new ConnectionRefused(getActivePeers()).encode());
                        out.flush();
                        ServerMain.log.info("Sending CONNECTION_REFUSED");
                    } catch (IOException e) {
                        ServerMain.log.warning("Failed writing CONNECTION_REFUSED");
                    } finally {
                        socket.close();
                    }
                }
            } catch (IOException e) {
                ServerMain.log.warning("Failed connecting to peer");
                e.printStackTrace();
            }
        }
    }

    @Override
    PeerConnection tryPeer(HostPort peerHostPort) {
        if (hasPeer(peerHostPort)) {
            return null;
        }

        try {
            Socket socket = new Socket(peerHostPort.hostname, peerHostPort.port);

            // find a name
            String name = server.getAnyName();
            PeerConnection peer = new PeerTCP(name, socket, server, true);
            addPeer(peer);
            // success: remove this peer from the set of peers to connect to
            ServerMain.log.info("Connected to peer " + name + " @ " + peerHostPort);
            return peer;
        } catch (IOException e) {
            ServerMain.log.warning("Connection to peer `" + peerHostPort + "` failed: " + e.getMessage());
            return null;
        }
    }
}
