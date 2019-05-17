package unimelb.bitbox;

import org.jetbrains.annotations.NotNull;
import unimelb.bitbox.messages.FileBytesRequest;
import unimelb.bitbox.messages.FileBytesResponse;
import unimelb.bitbox.messages.InvalidProtocol;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.ResponseFormatException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

// ELEANOR: Fix issue where the same file is transferred to several peers and terminates when the first transfer
//          finishes.
class FileTransfer {
    public final String pathName;
    public final PeerConnection peer;

    public FileTransfer(String pathName, PeerConnection peer) {
        this.pathName = pathName;
        this.peer = peer;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof FileTransfer) {
            FileTransfer rhs = (FileTransfer)other;
            return pathName.equals(rhs.pathName) && peer.equals(rhs.peer);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (pathName + peer.toString()).hashCode();
    }
}

/**
 * A ReadWriteThreadPool manages all the workers for reading file bytes and writing file bytes on this peer
 * in response to messages received from other peers.
 */
public class FileReadWriteThreadPool {
    private final FileSystemManager fsManager;
    private final ThreadPoolExecutor executor;
    private final ConcurrentHashMap<FileTransfer, Long> fileModifiedDates;

    public FileReadWriteThreadPool(@NotNull ServerMain server){
        this.fsManager = server.fileSystemManager;
        this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        this.fileModifiedDates = new ConcurrentHashMap<>();
    }

    public void addFile(PeerConnection peer, String pathName, JsonDocument fileDescriptor)
            throws ResponseFormatException {
        long fileSize = fileDescriptor.require("fileSize");
        long modified = fileDescriptor.require("lastModified");

        FileTransfer ft = new FileTransfer(pathName, peer);
        Long currentOrNull = fileModifiedDates.get(ft);

        if (currentOrNull != null) {
            // if it's not a newer file and we had a transfer already, ignore the request
            if (currentOrNull <= modified) {
                ServerMain.log.info(peer.name + ": received create/modify request, but was already transferring" +
                        "same or newer file");
                return;
            }
        }

        // start the transfer
        fileModifiedDates.put(ft, modified);
        sendReadRequest(peer, pathName, fileDescriptor, 0);
        ServerMain.log.info(peer.name + ": sent FILE_BYTES_REQUEST for " +
                pathName + " at position: [0/" + fileSize + "]");
    }

    /**
     * This method sends the first FILE_BYTES_REQUEST
     */
    public void sendReadRequest(PeerConnection peer, String pathName, JsonDocument fileDescriptor, long position)
            throws ResponseFormatException {
        // Send a byte request
        peer.sendMessage(new FileBytesRequest(pathName, fileDescriptor, position));
    }

    /**
     * This method adds and run a ReadWorker to read the bytes based on the FILE_BYTES_REQUEST message
     * received from other peers. It then encodes the content and sends a FILE_BYTES_RESPONSE message to reply.
     */
    public void readFile(PeerConnection peer, JsonDocument document) throws ResponseFormatException {
        // Get the min. value to determine the final length for byte read
        JsonDocument fileDescriptor = document.require("fileDescriptor");

        // Run a worker thread to read the bytes
        String pathName = document.require("pathName");
        long position = document.require("position");
        // ELEANOR: the length is the minimum of the bytes remaining and the block size
        long length = document.require("length");/*Math.min(fileDescriptor.getLong("fileSize") - position,
                               Long.parseLong(Configuration.getConfigurationValue("blockSize")));*/
        Runnable worker = new ReadWorker(peer, document, position, length);
        executor.execute(worker);

        long fileSize = fileDescriptor.require("fileSize");
        ServerMain.log.info(peer.name + ": read bytes of " + pathName +
                " at position: [" + position + "/" + fileSize + "]");
    }


    /**
     * This method checks if any encoded ByteBuffer is received successfully from other peers.
     * If any, it adds and runs a WriteWorker to write the bytes.
     * The WriteWorker will decide if the peer should send another FILE_BYTES_REQUEST.
     */
    public void writeFile(PeerConnection peer, JsonDocument document) throws ResponseFormatException {
        String pathName = document.require("pathName");
        long position = document.require("position");
        boolean status = document.require("status");
        long length = document.require("length"); // Use the agreed min. length between the peers

        JsonDocument fileDescriptor = document.require("fileDescriptor");
        long fileSize = fileDescriptor.require("fileSize");

        // Run a worker thread to write the bytes received
        if (status){
            Runnable worker = new WriteWorker(peer, document, position, length);
            executor.execute(worker);
            ServerMain.log.info(peer + ": write " + pathName +
                    " at position: [" + position + "/" + fileSize + "]");
        } else {
            FileTransfer ft = new FileTransfer(pathName, peer);
            try {
                fsManager.cancelFileLoader(pathName);
            } catch (IOException e) {
                ServerMain.log.warning("failed to delete file loader of " + pathName);
            }
            fileModifiedDates.remove(ft);
            // ELEANOR: it's useful to know /why/ we got an unsuccessful response ;)
            ServerMain.log.info("unsuccessful response: " + document.require("message"));
            ServerMain.log.info(peer.name + ": closed file loader of " + pathName);
        }
    }

    /**
     * The parent class of all file read and file write worker thread classes.
     */
    abstract class Worker implements Runnable {
        PeerConnection peer;
        JsonDocument document;
        JsonDocument fileDescriptor;
        String pathName;
        long position;
        long fileSize;

        public Worker(PeerConnection peer, JsonDocument document, long position) throws ResponseFormatException {
            this.peer = peer;
            this.document = document;
            this.fileDescriptor = document.require("fileDescriptor");
            this.pathName = document.require("pathName");
            this.position = position;
            this.fileSize = fileDescriptor.require("fileSize");
        }

        @Override
        public void run() {}

    }

    class WriteWorker extends Worker {

        private long length;

        public WriteWorker(PeerConnection peer, JsonDocument document, long position, long length)
                throws ResponseFormatException {
            super(peer, document, position);
            this.length = length;
        }

        @Override
        public void run() {
            // ELEANOR: off-by-one error here; was missing the last byte of each packet
            long nextPosition = position + length;

            // Write bytes
            try {
                String content = document.require("content");
                ByteBuffer decoded = ByteBuffer.wrap(Base64.getDecoder().decode(content));
                if (!fsManager.writeFile(pathName, decoded, position)) {
                    ServerMain.log.warning("Failed writing bytes: " + pathName);
                    cancelFile(peer, pathName);
                    return;
                }
            }
            catch (IOException e){
                ServerMain.log.info(peer.name + ": wrote file " + pathName +
                        " at position: [" + position + "/" + fileSize + "]");
            } catch (ResponseFormatException e) {
                peer.sendMessageAndClose(new InvalidProtocol("Missing content field"));
                return;
            }


            // Check if more bytes are needed. If yes, send another FileBytesRequest
            try {
                if (!fsManager.checkWriteComplete(pathName)) {
                    peer.sendMessage(new FileBytesRequest(pathName, fileDescriptor, nextPosition));
                    ServerMain.log.info(peer.name + ": sent FILE_BYTES_REQUEST for " + pathName +
                            " at position: [" + nextPosition + "/" + fileSize + "]");
                } else {
                    cancelFile(peer, pathName);
                    ServerMain.log.info(peer.name + ": received all bytes for " + pathName +
                            ". File created successfully");
                }
            }
            catch (NoSuchAlgorithmException | IOException e) {
                ServerMain.log.warning(peer.name + ": error closing file loader for " + pathName);
            }
            catch (OutOfMemoryError e){
                ServerMain.log.info(peer.name + ": not enough memory to write " + pathName +
                        " at position: [" + nextPosition + "/" + fileSize + "]");
            }
            catch (Exception e) {
                ServerMain.log.warning(peer.name + ": error writing " +
                        pathName + " at position: [" + nextPosition + "/" + fileSize + "]");
                // ELEANOR: if we're going to catch a generic `Exception`, we probably want a record of what happened
                e.printStackTrace();
            }
        }
    }

    private void cancelFile(PeerConnection peer, String pathName) {
        FileTransfer ft = new FileTransfer(pathName, peer);
        fileModifiedDates.remove(ft);
    }

    public void cancelPeerFiles(PeerConnection peer) {
        List<FileTransfer> toRemove = fileModifiedDates.keySet().stream()
                                                       .filter(ft -> ft.peer.equals(peer))
                                                       .collect(Collectors.toList());
        toRemove.forEach(fileModifiedDates::remove);
    }

    class ReadWorker extends Worker {
        private long length;

        public ReadWorker(PeerConnection peer, JsonDocument document, long position, long length)
                throws ResponseFormatException {
            super(peer, document, position);
            this.length = Configuration.getConfigurationValue("mode") == "udp" ? Math.min(length,8192) : length;

        }


        @Override
        public void run() {
            String content = "";
            String reply = FileBytesResponse.SUCCESS;
            String md5;
            long fileSize;
            try {
                final JsonDocument fileDescriptor = document.require("fileDescriptor");
                md5 = fileDescriptor.require("md5");
                fileSize = fileDescriptor.require("fileSize");
            } catch (ResponseFormatException e) {
                peer.sendMessageAndClose(new InvalidProtocol("Malformed message: " + e.getMessage()));
                return;
            }
            try {
                ByteBuffer byteBuffer = peer.server.fileSystemManager.readFile(md5, position, length);
                if (byteBuffer == null) {
                    reply = "no matching file found: " + md5 + ", " + position + ", " + length;
                } else {
                    content = Base64.getEncoder().encodeToString(byteBuffer.array());
                }
            } catch (OutOfMemoryError e) {
                reply = "length requested too large";
                ServerMain.log.severe(peer.name + ": error writing bytes of file " + pathName + " at [" +
                        position + "/" + fileSize + "]. The file size is too big: " + e.getMessage());
            } catch (Exception e) {
                reply = "unsuccessful read";
                e.printStackTrace();
                ServerMain.log.warning(peer.name + ": failed reading bytes of file " + pathName + " at [" +
                        position + "/" + fileSize + "]: " + e.getMessage());
            }
            try {
                peer.sendMessage(new FileBytesResponse(document, pathName, length, position, content, reply));
            } catch (ResponseFormatException e) {
                peer.sendMessageAndClose(new InvalidProtocol("Malformed message: " + e.getMessage()));
            }
        }
    }
}
