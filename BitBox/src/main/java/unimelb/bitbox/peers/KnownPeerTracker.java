package unimelb.bitbox.peers;

import unimelb.bitbox.util.concurrency.Iteration;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.TimestampedAddress;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class KnownPeerTracker {
    private static final List<TimestampedAddress> addresses = Collections.synchronizedList(new ArrayList<>());
    private static final String PEER_LIST_FILE = "peerlist";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final AtomicInteger maxConcurrent = new AtomicInteger();
    private static final AtomicReference<String> lastModifiedTimestamp = new AtomicReference<>();

    static {
        try {
            load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void load() {
        Set<TimestampedAddress> loaded = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(PEER_LIST_FILE))) {
            String line;
            reader.readLine();
            reader.readLine();
            reader.readLine();
            String record = reader.readLine();
            if (record == null) {
                return;
            }

            maxConcurrent.set(Integer.parseInt(record.split(": ")[0]));
            lastModifiedTimestamp.set(record.split("\\(")[1].split("\\)")[0]);
            while ((line = reader.readLine()) != null) {
                loaded.add(TimestampedAddress.parse(line));
            }
        } catch (FileNotFoundException ignored) {
            // This is fine, the file just might not exist yet
        } catch (IOException e) {
            e.printStackTrace();
        }

        addresses.addAll(loaded);
    }

    static void addAddress(HostPort localHostPort, HostPort advertised) {
        String result = localHostPort + " (claimed: " + advertised
                + (advertised.isAliased() ? ", resolved: " + advertised.asAliasedAddress()
                : "") + ")";
        TimestampedAddress newAddress = new TimestampedAddress(result);
        addresses.add(newAddress);
        worker.execute(KnownPeerTracker::write);
    }

    static void notifyPeerCount(int count) {
        int oldVal = maxConcurrent.get();
        if (maxConcurrent.accumulateAndGet(count, Math::max) > oldVal) {
            lastModifiedTimestamp.set(new Timestamp(new Date().getTime()).toString());
        }
    }

    private static void write() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PEER_LIST_FILE))) {
            Set<String> claimedAddresses = new HashSet<>();
            Set<String> actualAddresses = new HashSet<>();
            PriorityQueue<String> sortedConnections = new PriorityQueue<>();

            if (Iteration.forEachAsync(addresses, address -> {
                claimedAddresses.add(address.toString().split("claimed: ")[1]);
                actualAddresses.add(address.toString().split("] ")[1].split(":")[0]);
                sortedConnections.add(address.toString());
            })) {
                writer.write(claimedAddresses.size() + " unique peers\n");
                writer.write(actualAddresses.size() + " unique addresses\n");
                writer.write(addresses.size() + " unique connections\n");
                writer.write(maxConcurrent + ": record for concurrent peers (" + lastModifiedTimestamp + ")\n");

                while (!sortedConnections.isEmpty()) {
                    // Write the clients, sorted lexicographically, and joined with a newline.
                    writer.write(sortedConnections.poll() + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private KnownPeerTracker() {}
}