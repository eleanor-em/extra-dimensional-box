package unimelb.bitbox.server;

import unimelb.bitbox.client.ClientServer;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.peers.ReadWriteManager;
import unimelb.bitbox.util.concurrency.KeepAlive;
import unimelb.bitbox.util.config.CfgDependent;
import unimelb.bitbox.util.config.CfgEnumValue;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.config.Configuration;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.fs.FileSystemManager;
import unimelb.bitbox.util.fs.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.fs.FileSystemObserver;
import unimelb.bitbox.util.network.HostPort;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * A connection can use either TCP or UDP.
 */
enum ConnectionMode {
    TCP,
    UDP
}

public class PeerServer implements FileSystemObserver {

    /* Configuration values */
    private final CfgValue<Long> udpBlockSize = CfgValue.createLong("udpBlockSize");
    private final CfgValue<String> advertisedName = CfgValue.create("advertisedName");
    private final CfgValue<Integer> tcpPort = CfgValue.createInt("port");
    private final CfgValue<Integer> udpPort = CfgValue.createInt("udpPort");
    private final CfgEnumValue<ConnectionMode> mode = new CfgEnumValue<>("mode", ConnectionMode.class);
    private final CfgDependent<HostPort> hostPort = new CfgDependent<>(Arrays.asList(advertisedName, tcpPort, udpPort), this::calculateHostPort);
    private final CfgDependent<Long> blockSize = new CfgDependent<>(mode, this::calculateBlockSize);

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

    public static long getMaximumLength() {
        return get().mode.get() == ConnectionMode.TCP
                ? get().blockSize.get()
                : get().udpBlockSize.get();
    }
    public static HostPort getHostPort() {
        return get().hostPort.get();
    }
    public static ConnectionHandler getConnection() {
        return get().connection;
    }

    /**
     * Adds a message to the message processor's queue.
     */
    public static void enqueueMessage(ReceivedMessage message) {
        get().processor.add(message);
    }

    /* File system event handling */
    public static void synchroniseFiles(Peer peer) {
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

    private static PeerServer INSTANCE = null;

    public static void initialise() throws IOException {
        if (INSTANCE != null) {
            throw new RuntimeException("PeerServer initialised twice");
        }
        new PeerServer();
    }

    private static PeerServer get() {
        if (INSTANCE == null) {
            throw new RuntimeException("No peer server exists");
        }
        return INSTANCE;
    }

    private PeerServer() throws IOException {
        INSTANCE = this;

        // Create the file system manager
        CfgValue<String> path = CfgValue.create("path");
        path.setOnChanged(() -> log.warning("Path was changed in config, but will not be updated until restart"));
        fileSystemManager = new FileSystemManager(path.get(), this);

		// Create the processor thread
        KeepAlive.submit(processor);
		log.info("Processor thread started");

        // Start the connection handler
        setConnection(mode.get());
        mode.setOnChanged(newMode -> {
            connection.deactivate();
            setConnection(newMode);
        });
        hostPort.setOnChanged(() -> {
            connection.deactivate();
            setConnection(mode.get());
        });

        // Connect to the peers in the config file
        CfgValue<String[]> peersToConnect = CfgValue.create("peers", val -> val.split(","));
        peersToConnect.setOnChanged(connection::addPeerAddressAll);
        connection.addPeerAddressAll(peersToConnect.get());
        connection.retryPeers();

		// Create the server thread for the client
		KeepAlive.submit(new ClientServer());
		log.info("Server thread started");

		// Create the synchroniser thread
		KeepAlive.submit(this::regularlySynchronise);
		log.info("Synchroniser thread started");
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
        }
    }

    private long calculateBlockSize() {
        long blockSize;
        blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        if (mode.get() == ConnectionMode.UDP) {
            blockSize = Math.min(blockSize, udpBlockSize.get());
        }
        return blockSize;
    }

    private HostPort calculateHostPort() {
        int serverPort;
        serverPort = mode.get() == ConnectionMode.TCP
                     ? tcpPort.get()
                     : udpPort.get();
        return new HostPort(advertisedName.get(), serverPort);
    }

    private void setConnection(ConnectionMode mode) {
        connection = mode == ConnectionMode.TCP
                     ? new TCPConnectionHandler()
                     : new UDPConnectionHandler();
    }
}
