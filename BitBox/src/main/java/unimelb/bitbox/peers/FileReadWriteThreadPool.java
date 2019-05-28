package unimelb.bitbox.peers;

import org.jetbrains.annotations.NotNull;
import unimelb.bitbox.messages.FileBytesRequest;
import unimelb.bitbox.messages.FileBytesResponse;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.functional.algebraic.Maybe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

class FileTransfer {
    public final String pathName;
    public final Peer peer;

    public FileTransfer(String pathName, Peer peer) {
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
    private final PeerServer server;
    private final ThreadPoolExecutor executor;
    private final ConcurrentHashMap<FileTransfer, Long> fileModifiedDates = new ConcurrentHashMap<>();
    private final Set<Peer> storedPeers = ConcurrentHashMap.newKeySet();

    public FileReadWriteThreadPool(@NotNull PeerServer server){
        this.server = server;
        this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    }

    public void addFile(Peer peer, String pathName, FileDescriptor fileDescriptor) {
        FileTransfer ft = new FileTransfer(pathName, peer);

        // Check we're not already transferring a newer version
        if (Maybe.of(fileModifiedDates.get(ft))
                 .map(current -> current <= fileDescriptor.lastModified)
                 .fromMaybe(false)) {
            PeerServer.log.info(peer.getForeignName() + ": received create/modify request, but was already transferring" +
                    "same or newer file");
        }

        // start the transfer
        fileModifiedDates.put(ft, fileDescriptor.lastModified);
        sendReadRequest(peer, pathName, fileDescriptor, 0);
        PeerServer.log.info(peer.getForeignName() + ": sent FILE_BYTES_REQUEST for " +
                pathName + " at position: [0/" + fileDescriptor.fileSize + "]");
    }

    /**
     * This method sends the first FILE_BYTES_REQUEST
     */
    public void sendReadRequest(Peer peer, String pathName, FileDescriptor fileDescriptor, long position) {
        // Send a byte request
        peer.sendMessage(new FileBytesRequest(server, pathName, fileDescriptor, position));
    }

    /**
     * This method adds and run a ReadWorker to read the bytes based on the FILE_BYTES_REQUEST message
     * received from other peers. It then encodes the content and sends a FILE_BYTES_RESPONSE message to reply.
     */
    public void readFile(Peer peer, FileDescriptor fd, String pathName, long position, long length) {
        length = Math.min(server.getMaximumLength(), length);

        executor.execute(new ReadWorker(peer, fd, pathName, position, length));

        PeerServer.log.info(peer.getForeignName() + ": read bytes of " + pathName +
                " at position: [" + position + "/" + fd.fileSize + "]");
    }


    /**
     * This method checks if any networkEncoded ByteBuffer is received successfully from other peers.
     * If any, it adds and runs a WriteWorker to write the bytes.
     * The WriteWorker will decide if the peer should send another FILE_BYTES_REQUEST.
     */
    public void writeFile(Peer peer, FileDescriptor fd, String pathName, long position, long length, String content) {
        // Run a worker thread to write the bytes received
        Runnable worker = new WriteWorker(peer, fd, pathName, position, length, content);
        executor.execute(worker);
        PeerServer.log.info(peer + ": write " + pathName +
                " at position: [" + position + "/" + fd.fileSize + "]");
    }
    /**
     * The parent class of all file read and file write worker thread classes.
     */
    abstract class Worker implements Runnable {
        Peer peer;
        FileDescriptor fileDescriptor;
        String pathName;
        long position;
        long length;

        public Worker(Peer peer, FileDescriptor fd, String pathName, long position, long length) {
            this.peer = peer;
            this.fileDescriptor = fd;
            this.pathName = pathName;
            this.position = position;
            this.length = length;
        }

        @Override
        public void run() {}

    }

    class WriteWorker extends Worker {
        private String content;

        public WriteWorker(Peer peer, FileDescriptor fd, String pathName, long position, long length, String content) {
            super(peer, fd, pathName, position, length);

            this.content = content;

            storedPeers.add(peer);
            peer.addCloseTask(() -> cancelPeerFiles(peer));
        }

        @Override
        public void run() {
            long nextPosition = position + length;

            // Write bytes
            try {
                ByteBuffer decoded = ByteBuffer.wrap(Base64.getDecoder().decode(content));
                server.getFSManager().writeFile(pathName, decoded, position);
                PeerServer.log.info(peer.getForeignName() + ": wrote file " + pathName +
                        " at position: [" + position + "/" + fileDescriptor.fileSize + "]");
            }
            catch (IOException e){
                e.printStackTrace();
                PeerServer.log.warning(peer.getForeignName() + ": error writing bytes to " + pathName +
                        " at position: [" + position + "/" + fileDescriptor.fileSize + "] :" + e.getMessage());
                cancelFile(peer, pathName);
                return;
            }


            // Check if more bytes are needed. If yes, send another FileBytesRequest
            try {
                if (!server.getFSManager().checkWriteComplete(pathName)) {
                    peer.sendMessage(new FileBytesRequest(server, pathName, fileDescriptor, nextPosition));
                    PeerServer.log.info(peer.getForeignName() + ": sent FILE_BYTES_REQUEST for " + pathName +
                            " at position: [" + nextPosition + "/" + fileDescriptor.fileSize + "]");
                } else {
                    cancelFile(peer, pathName);
                    PeerServer.log.info(peer.getForeignName() + ": received all bytes for " + pathName +
                            ". File created successfully");
                }
            } catch (IOException e) {
                PeerServer.log.warning(peer.getForeignName() + ": error closing file loader for " + pathName);
            } catch (OutOfMemoryError e){
                PeerServer.log.info(peer.getForeignName() + ": not enough memory to write " + pathName +
                        " at position: [" + nextPosition + "/" + fileDescriptor.fileSize + "]");
            } catch (Exception e) {
                PeerServer.log.severe(peer.getForeignName() + ": unknown error writing " +
                        pathName + " at position: [" + nextPosition + "/" + fileDescriptor.fileSize + "]: "
                        + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void cancelFile(Peer peer, String pathName) {
        FileTransfer ft = new FileTransfer(pathName, peer);
        fileModifiedDates.remove(ft);
    }

    public void cancelPeerFiles(Peer peer) {
        List<FileTransfer> toRemove = fileModifiedDates.keySet().stream()
                                                       .filter(ft -> ft.peer.equals(peer))
                                                       .collect(Collectors.toList());
        toRemove.forEach(fileModifiedDates::remove);
        // Clear any file transfers associated with this peer
        toRemove.forEach(ft -> {
            try {
                if (!server.getFSManager().cancelFileLoader(ft.pathName)) {
                    throw new IOException(ft.pathName);
                }
                PeerServer.log.info(ft.peer.getForeignName() + ":Cancelling transfer of " + ft.pathName);
            } catch (IOException e) {
                PeerServer.log.warning(ft.peer.getForeignName() + ": failed cancelling file loader: "+ e.getMessage());
            }
        });
        synchronized (storedPeers) {
            storedPeers.remove(peer);
        }
    }

    class ReadWorker extends Worker {
        public ReadWorker(Peer peer, FileDescriptor fd, String pathName, long position, long length) {
            super(peer, fd, pathName, position, length);
        }


        @Override
        public void run() {
            AtomicReference<String> content = new AtomicReference<>("");
            AtomicReference<String> reply = new AtomicReference<>(FileBytesResponse.SUCCESS);
            try {
                server.getFSManager().readFile(fileDescriptor.md5, position, length)
                      .match(byteBuffer -> content.set(Base64.getEncoder().encodeToString(byteBuffer.array())),
                             () -> reply.set("no matching file found: " + fileDescriptor.md5 + ", " + position + ", " + length));
            } catch (OutOfMemoryError e) {
                reply.set("length requested too large");
                PeerServer.log.warning(peer.getForeignName() + ": error writing bytes of file " + pathName + " at [" +
                        position + "/" + fileDescriptor.fileSize + "]. The file size is too big: " + e.getMessage());
            } catch (IOException e) {
                reply.set("unsuccessful read");
                PeerServer.log.warning(peer + ": failed reading bytes of file " + pathName + " at [" +
                        position + "/" + fileDescriptor.fileSize + "]: " + e.getMessage());
            } catch (Exception e) {
                reply.set("unrecognised error: " + e.getClass().getName() + ": " + e.getMessage());
                PeerServer.log.severe(peer + ": failed reading bytes of file " + pathName + " at [" +
                        position + "/" + fileDescriptor.fileSize + "]: " + reply);
                e.printStackTrace();
            }

            peer.sendMessage(new FileBytesResponse(fileDescriptor, pathName, length, position, content.get(), reply.get(), false));
        }
    }
}
