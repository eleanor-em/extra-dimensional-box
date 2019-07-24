package unimelb.bitbox.server;

import functional.algebraic.Maybe;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.peers.ReadWriteManager;
import unimelb.bitbox.util.concurrency.KeepAlive;
import unimelb.bitbox.util.config.CfgDependent;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.fs.FileSystemEvent;
import unimelb.bitbox.util.fs.FileSystemManager;
import unimelb.bitbox.util.fs.FileSystemObserver;
import unimelb.bitbox.util.network.HostPort;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The central class to hold various values.
 *
 * @author Eleanor McMurtry
 * @author Andrea Law
 * @author Benjamin(Jingyi Li) Li
 */
public class PeerServer implements FileSystemObserver {
    /* Configuration values */
    private final CfgValue<Long> blockSize = CfgValue.createLong("blockSize");
    private final CfgValue<String> advertisedName = CfgValue.createString("advertisedName");
    private final CfgValue<Integer> port = CfgValue.createInt("port");
    private final CfgDependent<HostPort> hostPort = new CfgDependent<>(Arrays.asList(advertisedName, port), this::calculateHostPort);

    /* Objects used by the class */
    private final Logger log = Logger.getLogger(PeerServer.class.getName());
    private final FileSystemManager fileSystemManager;
    private final MessageProcessor processor = new MessageProcessor();
    private final ReadWriteManager rwManager = new ReadWriteManager();
    private ConnectionHandler connection;

    /* Getters */
    public static FileSystemManager fsManager() {
        return get().fileSystemManager;
    }
    public static ReadWriteManager rwManager() { return get().rwManager; }

    public static Logger log() {
        return get().log;
    }

    public static long maxBlockSize() {
        return get().blockSize.get();
    }
    public static HostPort hostPort() {
        return get().hostPort.get();
    }
    public static ConnectionHandler connection() {
        return get().connection;
    }

    /**
     * Adds a message to the message processor's queue.
     */
    public static void enqueueMessage(ReceivedMessage message) {
        get().processor.add(message);
    }

    /* File system event handling */
    static void synchroniseFiles(Peer peer) {
        fsManager().generateSyncEvents().forEach(ev -> peer.sendMessage(processEvent(ev)));
    }

    @Override
    public void processFileSystemEvent(FileSystemEvent ev) {
        connection.broadcastMessage(processEvent(ev));
    }

    private static Message processEvent(FileSystemEvent ev) {
        FileDescriptor fd = ev.fileDescriptor;
        switch (ev.event) {
            case DIRECTORY_CREATE:
                return new DirectoryCreateRequest(ev.pathName);
            case DIRECTORY_DELETE:
                return new DirectoryDeleteRequest(ev.pathName);
            case FILE_CREATE:
                return new FileCreateRequest(fd);
            case FILE_DELETE:
                return new FileDeleteRequest(fd);
            case FILE_MODIFY:
                return new FileModifyRequest(fd);
            default:
                throw new RuntimeException("unrecognised event " + ev.event);
        }
    }
    private static void synchroniseFiles() {
        fsManager().generateSyncEvents().forEach(get()::processFileSystemEvent);
    }

    public static int getPeerCount() {
        return get().connection.getActivePeers().size();
    }

    /* Singleton implementation */

    private static Maybe<PeerServer> INSTANCE = Maybe.nothing();

    public static void initialise() throws IOException {
        if (INSTANCE.isJust()) {
            throw new RuntimeException("PeerServer initialised twice");
        }
        new PeerServer();
    }

    public static PeerServer get() {
        return INSTANCE.get();
    }

    private PeerServer() throws IOException {
        INSTANCE = Maybe.just(this);
        log.setLevel(Level.FINER);

        // Create the file system manager
        CfgValue<String> path = CfgValue.createString("path");
        path.setOnChanged(() -> log.warning("Path was changed in config, but will not be updated until restart"));
        fileSystemManager = new FileSystemManager(path.get());

		// Create the processor thread
        KeepAlive.submit(processor);
		log.fine("Processor thread started");

        // Start the connection handler
        connection = new ConnectionHandler();
        hostPort.setOnChanged(this::resetConnection);

        // Connect to the peers in the config file
        CfgValue<String[]> peersToConnect = CfgValue.create("peers", val -> val.split(","));
        peersToConnect.setOnChanged(connection::addPeerAddressAll);
        connection.addPeerAddressAll(peersToConnect.get());
        connection.retryPeers();

		// Create the synchroniser thread
		KeepAlive.submit(this::regularlySynchronise);
		log.fine("Synchroniser thread started");

		log.info("BitBox Peer online.");
	}

    private void regularlySynchronise() {
        CfgValue<Integer> syncInterval = CfgValue.createInt("syncInterval");
        while (true) {
            try {
                Thread.sleep(syncInterval.get() * 1000);
            } catch (InterruptedException e) {
                log.warning("Synchronise thread interrupted");
            }
            synchroniseFiles();
            rwManager.reportDownloads();
        }
    }

    private HostPort calculateHostPort() {
        return new HostPort(advertisedName.get(), port.get());
    }

    private void resetConnection() {
        connection.deactivate();
        connection = new ConnectionHandler();
    }
}
