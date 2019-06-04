package unimelb.bitbox.server;

import unimelb.bitbox.client.ClientServer;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.peers.FileReadWriteThreadPool;
import unimelb.bitbox.server.connections.ConnectionHandler;
import unimelb.bitbox.server.connections.TCPConnectionHandler;
import unimelb.bitbox.server.connections.UDPConnectionHandler;
import unimelb.bitbox.util.concurrency.KeepAlive;
import unimelb.bitbox.util.config.CfgDependent;
import unimelb.bitbox.util.config.CfgEnumValue;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.config.Configuration;
import unimelb.bitbox.util.fs.FileSystemManager;
import unimelb.bitbox.util.fs.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.fs.FileSystemObserver;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.JSONDocument;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

public class PeerServer implements FileSystemObserver {
    // Enum data
    enum ConnectionMode {
        TCP,
        UDP
    }

    // Configuration values
    private final CfgValue<Long> udpBlockSize = CfgValue.createLong("udpBlockSize");
    private final CfgValue<String> advertisedName = CfgValue.create("advertisedName");
    private final CfgValue<Integer> tcpPort = CfgValue.createInt("port");
    private final CfgValue<Integer> udpPort = CfgValue.createInt("udpPort");
    private final CfgEnumValue<ConnectionMode> mode = new CfgEnumValue<>("mode", ConnectionMode.class);
    private final CfgDependent<HostPort> hostPort = new CfgDependent<>(Arrays.asList(advertisedName, tcpPort, udpPort), this::calculateHostPort);
    private final CfgDependent<Long> blockSize = new CfgDependent<>(mode, this::calculateBlockSize);

    // Objects used by the class
    private final Logger log = Logger.getLogger(PeerServer.class.getName());
    private final FileSystemManager fileSystemManager;
    private final MessageProcessor processor = new MessageProcessor();
    private final FileReadWriteThreadPool rwManager = new FileReadWriteThreadPool();
    private ConnectionHandler connection;

    public static FileSystemManager fsManager() {
        return get().fileSystemManager;
    }
    public static FileReadWriteThreadPool rwManager() { return get().rwManager; }

    public static void logInfo(String message) {
        get().log.info(message);
    }
    public static void logWarning(String message) {
        get().log.warning(message);
    }
    public static void logSevere(String message) {
        get().log.severe(message);
    }

    // Getters for data needed by other classes
    public static long getMaximumLength() {
        if (get().mode.get() == ConnectionMode.TCP) {
            return get().blockSize.get();
        } else {
            return get().udpBlockSize.get();
        }
    }
    public static HostPort getHostPort() {
        return get().hostPort.get();
    }
    public static ConnectionHandler getConnection() {
        return get().connection;
    }

    private static PeerServer INSTANCE;

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

    // Message handling
    public static void enqueueMessage(ReceivedMessage message) {
        get().processor.add(message);
    }

    // Event handling
    @Override
    public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
        JSONDocument fileDescriptor = fileSystemEvent.fileDescriptor.toJSON();
        switch (fileSystemEvent.event) {
            case DIRECTORY_CREATE:
                connection.broadcastMessage(new DirectoryCreateRequest(fileSystemEvent.pathName));
                break;
            case DIRECTORY_DELETE:
                connection.broadcastMessage(new DirectoryDeleteRequest(fileSystemEvent.pathName));
                break;
            case FILE_CREATE:
                connection.broadcastMessage(new FileCreateRequest(fileDescriptor, fileSystemEvent.pathName));
                break;
            case FILE_DELETE:
                connection.broadcastMessage(new FileDeleteRequest(fileDescriptor, fileSystemEvent.pathName));
                break;
            case FILE_MODIFY:
                connection.broadcastMessage(new FileModifyRequest(fileDescriptor, fileSystemEvent.pathName));
                break;
        }
    }

    public static void synchroniseFiles() {
        fsManager().generateSyncEvents()
                   .forEach(get()::processFileSystemEvent);
    }

    public static int getPeerCount() {
        return get().connection.getActivePeers().size();
    }

    private PeerServer() throws NumberFormatException, IOException {
        INSTANCE = this;

        // initialise things
        CfgValue<String> path = CfgValue.create("path");
        path.setOnChanged(() -> log.warning("Path was changed in config, but will not be updated until restart"));
        fileSystemManager = new FileSystemManager(path.get(), this);

		// create the processor thread
        KeepAlive.submit(processor);
		log.info("Processor thread started");

        // start the peer connection thread
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

		// create the server thread for the client
		KeepAlive.submit(new ClientServer());
		log.info("Server thread started");

		// create the synchroniser thread
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

    // Updates for config value changes
    private long calculateBlockSize() {
        long blockSize;
        blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        if (mode.get() == ConnectionMode.UDP) {
            blockSize = Math.min(blockSize, 8192);
        }
        return blockSize;
    }

    private HostPort calculateHostPort() {
        int serverPort;
        if (mode.get() == ConnectionMode.TCP) {
            serverPort = tcpPort.get();
        } else {
            serverPort = udpPort.get();
        }
        return new HostPort(advertisedName.get(), serverPort);
    }

    private void setConnection(ConnectionMode mode) {
        if (mode == ConnectionMode.TCP) {
            connection = new TCPConnectionHandler();
        } else {
            connection = new UDPConnectionHandler();
        }
    }
}
