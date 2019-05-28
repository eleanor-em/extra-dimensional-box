package unimelb.bitbox.server;

import unimelb.bitbox.client.Server;
import unimelb.bitbox.messages.*;
import unimelb.bitbox.peers.FileReadWriteThreadPool;
import unimelb.bitbox.peers.KnownPeerTracker;
import unimelb.bitbox.server.connections.ConnectionHandler;
import unimelb.bitbox.server.connections.TCPConnectionHandler;
import unimelb.bitbox.server.connections.UDPConnectionHandler;
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

public class ServerMain implements FileSystemObserver {
    private MessageProcessingThread processor;

    public static final Logger log = Logger.getLogger(ServerMain.class.getName());
    public final FileSystemManager fileSystemManager;
    private static final long UDP_MAX_BLOCK = 8192;

    private static final CfgValue<String> advertisedName = CfgValue.create("advertisedName");
    private static final CfgValue<Integer> tcpPort = CfgValue.createInt("port");
    private static final CfgValue<Integer> udpPort = CfgValue.createInt("udpPort");
    private static final CfgEnumValue<ConnectionMode> mode = new CfgEnumValue<>("mode", ConnectionMode.class);
    private static final CfgValue<String[]> peersToConnect = CfgValue.create("peers", val -> val.split(","));
    private final CfgDependent<HostPort> hostPort = new CfgDependent<>(Arrays.asList(advertisedName, tcpPort, udpPort),
                                                                              this::calculateHostPort);
    private final CfgDependent<Long> blockSize = new CfgDependent<>(mode, this::calculateBlockSize);

    public long getMaximumLength() {
        if (mode.get() == ConnectionMode.TCP) {
            return blockSize.get();
        } else {
            return UDP_MAX_BLOCK;
        }
    }
    public HostPort getHostPort() {
        return hostPort.get();
    }
    public long getBlockSize() {
        return blockSize.get();
    }

    public FileReadWriteThreadPool getReadWriteManager() {
        return processor.rwManager;
    }

    public ConnectionHandler getConnection() {
        return connection;
    }

    private ConnectionHandler connection;

    public void restartProcessingThread() {
        processor = new MessageProcessingThread(this);
        processor.start();
    }

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
        if (connection != null) {
            connection.deactivate();
        }
        if (mode == ConnectionMode.TCP) {
            connection = new TCPConnectionHandler(this);
        } else {
            connection = new UDPConnectionHandler(this);
        }
    }

    public ServerMain() throws NumberFormatException, IOException {
        // initialise things
        KnownPeerTracker.load();
        fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
        processor = new MessageProcessingThread(this);

        // load the mode
        // data read from the config file

		// create the processor thread
		processor.start();
		log.info("Processor thread started");


        // start the peer connection thread
        setConnection(mode.get());
        mode.setOnChanged(this::setConnection);
        hostPort.setOnChanged(() -> setConnection(mode.get()));

        peersToConnect.setOnChanged(connection::addPeerAddressAll);
        connection.addPeerAddressAll(peersToConnect.get());
        connection.retryPeers();

		// ELEANOR: terminology is confusing, so we introduce consistency
		// create the server thread for the client
		new Thread(new Server(this)).start();
		log.info("Server thread started");

		// create the synchroniser thread
		new Thread(this::regularlySynchronise).start();
	}

    /**
     * Adds a message to the queue of messages to be processed.
     */
    public void enqueueMessage(ReceivedMessage message) {
        processor.messages.add(message);
    }

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

    /**
     * Generate the synchronisation events, and send them to peers.
     */
    public void synchroniseFiles() {
        fileSystemManager.generateSyncEvents()
                .forEach(this::processFileSystemEvent);
    }

    private void regularlySynchronise() {
        final int SYNC_INTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
        try {
            while (true) {
                try {
                    Thread.sleep(SYNC_INTERVAL * 1000);
                } catch (InterruptedException e) {
                    log.warning("Synchronise thread interrupted");
                }
                synchroniseFiles();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            log.severe("Restarting synchroniser thread");
            new Thread(this::regularlySynchronise).start();
        }
    }

    enum ConnectionMode {
        TCP,
        UDP
    }
}
