package unimelb.bitbox.peers;

import unimelb.bitbox.server.ServerMain;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.HostPortParseException;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KnownPeerTracker {
    private static final Set<HostPort> addresses = ConcurrentHashMap.newKeySet();
    private static final String PEER_LIST_FILE = "peerlist";
    private static WriteAddresses worker = new WriteAddresses();

    public static void load() {
        Set<HostPort> loaded = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(PEER_LIST_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    // File contains details after a space
                    loaded.add(HostPort.fromAddress(line.split(" ")[0]));
                } catch (HostPortParseException e) {
                    ServerMain.log.warning("Failed to parse stored peer `" + line + "`");
                }
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
        new Thread(worker).start();
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