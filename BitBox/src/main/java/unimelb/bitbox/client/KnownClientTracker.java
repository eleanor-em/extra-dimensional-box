package unimelb.bitbox.client;

import unimelb.bitbox.util.concurrency.Iteration;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tracks the clients that we have received connections from.
 */
class KnownClientTracker {
    private static final List<String> clients = Collections.synchronizedList(new ArrayList<>());
    private static final String CLIENT_LIST_FILE = "clientlist";

    // Use a single thread because it doesn't matter if this service is a bit slow
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();

    /**
     * Loads the previous client file.
     */
    private static void load() {
        Collection<String> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(CLIENT_LIST_FILE))) {
            String line;
            // First 3 lines are header files
            reader.readLine();
            reader.readLine();
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                // Format is [timestamp] address
                String date = line.split("]")[0].substring(1);
                String rest = line.split("]")[1];

                // Ensure the timestamp is padded to 23 characters for consistency
                if (date.length() < 23) {
                    date += " ";
                }

                // Don't add directly to the client list so that the synchronised operation is only performed after
                // the file has been closed, for performance.
                loaded.add("[" + date + "]" + rest);
            }
        } catch (FileNotFoundException ignored) {
            // This is fine, the file just might not exist yet
        } catch (IOException e) {
            e.printStackTrace();
        }

        clients.addAll(loaded);
    }

    /*
     * Attempt to load the current file. If we fail, just start with a blank list.
     */
    static {
        try {
            load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a client to the tracking list.
     */
    static void addClient(ClientConnection client) {
        String stamp = new Timestamp(new Date().getTime()).toString();
        stamp = String.format("[%-23s] ", stamp);
        clients.add(stamp + client.getIdent() + ": " + client.getAddress());
        worker.execute(KnownClientTracker::write);
    }

    /**
     * Runnable action to actually write to the file.
     */
    private static void write() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CLIENT_LIST_FILE))) {
            final Set<String> idents = new HashSet<>();
            final Set<String> addresses = new HashSet<>();
            final PriorityQueue<String> sortedClients = new PriorityQueue<>();

            // Attempt to iterate over the clients. If we fail, then we added another, so we'll try again anyway.
            if (Iteration.forEachAsync(clients, client -> {
                        idents.add(client.split("] ")[1].split(":")[0]);
                        addresses.add(client.split(": ")[1]);
                        sortedClients.add(client);
                    })) {
                // Write collected statistics
                writer.write(clients.size() + " unique connections\n");
                writer.write(idents.size() + " unique idents\n");
                writer.write(addresses.size() + " unique addresses\n");

                while (!sortedClients.isEmpty()) {
                    // Write the clients, sorted lexicographically, and joined with a newline.
                    writer.write(sortedClients.poll() + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private KnownClientTracker() {}
}