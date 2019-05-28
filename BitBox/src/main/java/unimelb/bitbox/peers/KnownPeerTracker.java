package unimelb.bitbox.peers;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.network.HostPort;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KnownPeerTracker {
    private static final Set<HostPort> addresses = ConcurrentHashMap.newKeySet();
    private static final String PEER_LIST_FILE = "peerlist";
    private static ExecutorService worker = Executors.newSingleThreadExecutor();

    public static void load() {
        Set<HostPort> loaded = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(PEER_LIST_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // File contains details after a space
                HostPort.fromAddress(line.split(" ")[0])
                        .match(err -> PeerServer.log.warning(err.getMessage()),
                               loaded::add);
            }
        } catch (FileNotFoundException ignored) {
            // This is fine, the file just might not exist yet
        } catch (IOException e) {
            e.printStackTrace();
        }

        synchronized (addresses) {
            addresses.addAll(loaded);
        }
    }

    static synchronized void addAddress(HostPort hostPort) {
        addresses.add(hostPort);
        worker.submit(new WriteAddresses());
    }

    private static class WriteAddresses implements Runnable {
        @Override
        public synchronized void run() {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(PEER_LIST_FILE))) {
                StringBuilder builder = new StringBuilder();
                synchronized (addresses) {
                    for (HostPort hostPort : addresses) {
                        builder.append(hostPort.asAliasedAddress())
                               .append(" (via ")
                               .append(hostPort.asAddress())
                               .append(")\n");
                    }
                }
                writer.write(builder.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private KnownPeerTracker() {}
}