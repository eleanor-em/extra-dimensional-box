package unimelb.bitbox.server;

import unimelb.bitbox.client.ClientServer;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.peers.KnownPeerTracker;
import unimelb.bitbox.server.connections.ConnectionHandler;
import unimelb.bitbox.server.connections.TCPConnectionHandler;
import unimelb.bitbox.server.connections.UDPConnectionHandler;
import unimelb.bitbox.server.response.MessageProcessor;
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
    // Configuration values
    private final CfgValue<Long> udpBlockSize = CfgValue.createLong("udpBlockSize");
    private final CfgValue<String> advertisedName = CfgValue.create("advertisedName");
    private final CfgValue<Integer> tcpPort = CfgValue.createInt("port");
    private final CfgValue<Integer> udpPort = CfgValue.createInt("udpPort");
    private final CfgEnumValue<ConnectionMode> mode = new CfgEnumValue<>("mode", ConnectionMode.class);
    private final CfgDependent<HostPort> hostPort = new CfgDependent<>(Arrays.asList(advertisedName, tcpPort, udpPort), this::calculateHostPort);
    private final CfgDependent<Long> blockSize = new CfgDependent<>(mode, this::calculateBlockSize);

    // Objects used by the class
    public static final Logger log = Logger.getLogger(PeerServer.class.getName());
    private final MessageProcessor processor = new MessageProcessor(this);
    private FileSystemManager fileSystemManager;
    private ConnectionHandler connection;

    // Getters for data needed by other classes
    public FileSystemManager getFSManager() {
        return fileSystemManager;
    }
    public long getMaximumLength() {
        if (mode.get() == ConnectionMode.TCP) {
            return blockSize.get();
        } else {
            return udpBlockSize.get();
        }
    }
    public HostPort getHostPort() {
        return hostPort.get();
    }
    public ConnectionHandler getConnection() {
        return connection;
    }

    public PeerServer() throws NumberFormatException, IOException {
        // initialise things
        KnownPeerTracker.load();
        CfgValue<String> path = CfgValue.create("path");

        fileSystemManager = new FileSystemManager(path.get(), this);
        path.setOnChangedThrowable(() -> {
            fileSystemManager.interrupt();
            fileSystemManager = new FileSystemManager(path.get(), this);
        });

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
		KeepAlive.submit(new ClientServer(this));
		log.info("Server thread started");

		// create the synchroniser thread
		KeepAlive.submit(this::regularlySynchronise);
		log.info("Synchroniser thread started");
	}

	// Message handling
    public void enqueueMessage(ReceivedMessage message) {
        processor.add(message);
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

    public void synchroniseFiles() {
        fileSystemManager.generateSyncEvents()
                         .forEach(this::processFileSystemEvent);
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

    // Enum data
    enum ConnectionMode {
        TCP,
        UDP
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
            connection = new TCPConnectionHandler(this);
        } else {
            connection = new UDPConnectionHandler(this);
        }
    }
}
