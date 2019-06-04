package unimelb.bitbox.peers;

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
    private final ThreadPoolExecutor executor;
    private final ConcurrentHashMap<FileTransfer, Long> fileModifiedDates = new ConcurrentHashMap<>();
    private final Set<Peer> storedPeers = ConcurrentHashMap.newKeySet();

    public FileReadWriteThreadPool(){
        this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    }

    public void addFile(Peer peer, String pathName, FileDescriptor fileDescriptor) {
        FileTransfer ft = new FileTransfer(pathName, peer);

        // Check we're not already transferring a newer version
        if (Maybe.of(fileModifiedDates.get(ft))
                 .map(current -> current <= fileDescriptor.lastModified)
                 .fromMaybe(false)) {
            PeerServer.logInfo(peer.getForeignName() + ": received create/modify request, but was already transferring" +
                    "same or newer file");
        } else {
            // start the transfer
            fileModifiedDates.put(ft, fileDescriptor.lastModified);
            sendReadRequest(peer, pathName, fileDescriptor, 0);
            PeerServer.logInfo(peer.getForeignName() + ": sent FILE_BYTES_REQUEST for " +
                    pathName + " at position: [0/" + fileDescriptor.fileSize + "]");
        }
    }

    /**
     * This method sends the first FILE_BYTES_REQUEST
     */
    public void sendReadRequest(Peer peer, String pathName, FileDescriptor fileDescriptor, long position) {
        // Send a byte request
        peer.sendMessage(new FileBytesRequest(pathName, fileDescriptor, position));
    }

    /**
     * This method adds and run a ReadWorker to read the bytes based on the FILE_BYTES_REQUEST message
     * received from other peers. It then encodes the content and sends a FILE_BYTES_RESPONSE message to reply.
     */
    public void readFile(Peer peer, String pathName, FileDescriptor fd, long position, long length) {
        length = Math.min(PeerServer.getMaximumLength(), length);

        executor.execute(new ReadWorker(peer, fd, pathName, position, length));

        PeerServer.logInfo(peer.getForeignName() + ": read bytes of " + pathName +
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
        PeerServer.logInfo(peer + ": write " + pathName +
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
                PeerServer.fsManager().createIfNotLoading(pathName, fileDescriptor);
                PeerServer.fsManager().writeFile(pathName, decoded, position);
                PeerServer.logInfo(peer.getForeignName() + ": wrote file " + pathName +
                        " at position: [" + position + "/" + fileDescriptor.fileSize + "]");
            }
            catch (IOException e){
                PeerServer.logWarning(peer.getForeignName() + ": error writing bytes to " + pathName +
                        " at position: [" + position + "/" + fileDescriptor.fileSize + "] :" + e.getMessage());
                cancelFile(peer, pathName);
                return;
            }


            // Check if more bytes are needed. If yes, send another FileBytesRequest
            PeerServer.fsManager().checkWriteComplete(pathName)
                      .ok(res -> {
                          if (!res) {
                              sendReadRequest(peer, pathName, fileDescriptor, nextPosition);
                              PeerServer.logInfo(peer.getForeignName() + ": sent FILE_BYTES_REQUEST for " + pathName +
                                      " at position: [" + nextPosition + "/" + fileDescriptor.fileSize + "]");
                          } else {
                              cancelFile(peer, pathName);
                              PeerServer.logInfo(peer.getForeignName() + ": received all bytes for " + pathName +
                                      ". File created successfully");
                          }
                      })
                      .err(err -> PeerServer.logWarning(peer.getForeignName() + ": error closing file loader for " + pathName));
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
        toRemove.forEach(ft -> PeerServer.fsManager().cancelFileLoader(ft.pathName)
                  .ok(res -> {
                      if (res) {
                          PeerServer.logInfo(ft.peer.getForeignName() + ": cancelling transfer of " + ft.pathName);
                      } else {
                          PeerServer.logWarning(ft.peer.getForeignName() + ": tracked file " + ft.pathName + " not found");
                      }
                  })
                  .err(err -> PeerServer.logWarning(ft.peer.getForeignName() + ": failed cancelling file loader: "+ err.getMessage())));
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
            peer.sendMessage(new FileBytesResponse(pathName, fileDescriptor, length, position, peer));
        }
    }
}
