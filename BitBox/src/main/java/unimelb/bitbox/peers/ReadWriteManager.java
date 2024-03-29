package unimelb.bitbox.peers;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.fs.FileDescriptor;
import unimelb.bitbox.util.network.Conversion;
import unimelb.bitbox.util.network.FilePacket;
import unimelb.bitbox.util.network.FileTransfer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * A ReadWriteThreadPool manages all the workers for reading file bytes and writing file bytes on this peer
 * in response to messages received from other peers.
 *
 * @author Andrea Law
 * @author Eleanor McMurtry
 */
public class ReadWriteManager {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Set<FileTransfer> downloads = ConcurrentHashMap.newKeySet();

    /**
     * Initiate a transfer.
     * @param ft the transfer object
     */
    private void addFile(FileTransfer ft) {
        // Check for existing transfers of the same file
        for (Iterator<FileTransfer> it = downloads.iterator(); it.hasNext();) {
            FileTransfer existing = it.next();
            if (existing.pathName().equals(ft.pathName())) {
                // If the existing transfer is for an older version, cancel it. Otherwise, cancel the new transfer
                if (existing.fileDescriptor.lastModified() < ft.fileDescriptor.lastModified()) {
                    PeerServer.fsManager().cancelFileLoader(existing);
                    it.remove();
                } else {
                    PeerServer.log().fine(ft.peer.getForeignName() + ": received create/modify request, but was already transferring same or newer file");
                    return;
                }
            }
        }

        downloads.add(ft);
        ft.sendInitialBytesRequest();
    }

    public void addFile(Peer peer, FileDescriptor fd) {
        addFile(new FileTransfer(peer, fd));
    }


    /**
     * Read the provided chunk of the provided file, and send FILE_BYTES_RESPONSE to the peer
     */
    public void readFile(FilePacket packet) {
        executor.execute(packet::sendBytesResponse);
    }
    /**
     * Write the provided chunk to the provided file, and send another FILE_BYTES_REQUEST if necessary
     * @param content   the actual bytes to write, encoded in base 64
     */
    public void writeFile(FilePacket packet, String content) {
        executor.execute(new WriteWorker(packet, content));
    }

    public void reportDownloads() {
        AtomicReference<Long> totalWaiting = new AtomicReference<>(0L);
        AtomicReference<Long> totalDone = new AtomicReference<>(0L);

        StringBuilder inProgress = new StringBuilder();
        downloads.forEach(ft -> {
            float completion = ft.getCompletion();
            if (completion == 0) {
                ft.sendInitialBytesRequest();
            }

            totalDone.updateAndGet(v -> v + (long) (completion / 100 * ft.fileDescriptor.fileSize()));
            totalWaiting.updateAndGet(v -> v + ft.fileDescriptor.fileSize());

            inProgress.append(String.format("In progress (%04.1f%% complete): %s\n", completion, ft.pathName()));
        });
        if (totalWaiting.get() > 0) {
            float completion = (float) totalDone.get() / (float) totalWaiting.get() * 100;
            inProgress.append("Total: ")
                    .append(Conversion.humanFileSize(totalDone.get()))
                    .append(" / ")
                    .append(Conversion.humanFileSize(totalWaiting.get()))
                    .append(" (")
                    .append(String.format("%.1f", completion))
                    .append("%)");

            PeerServer.log().info("\n" + inProgress);
        }
    }

    private class WriteWorker implements Runnable {
        private final String content;
        private final FilePacket packet;

        WriteWorker(FilePacket packet, String content) {
            this.content = content;
            this.packet = packet;
            updateFile(packet);

            // When the peer that responded closes, we need to cancel any transfers they were performing
            packet.peer().addCloseTask(() -> cancelPeerFiles(packet.peer()));
        }

        @Override
        public void run() {
            // Write bytes
            try {
                ByteBuffer decoded = ByteBuffer.wrap(Base64.getDecoder().decode(content));
                packet.writeData(decoded);
                PeerServer.log().fine(packet.peer().getForeignName() + ": wrote bytes to " + packet.pathName() +
                        " at position: [" + packet.position + "/" + packet.fd().fileSize() + "]");
            }
            catch (IOException e){
                PeerServer.log().warning(packet.peer().getForeignName() + ": error writing bytes to " + packet.pathName() +
                        " at position: [" + packet.position + "/" + packet.fd().fileSize() + "]: " + e.getMessage());
                cancelFile(packet);
                return;
            }


            // Check if more bytes are needed
            PeerServer.fsManager().checkWriteComplete(packet.fd())
                      .ifOk(res -> {
                          // If the write isn't finished, send another request
                          if (res) {
                              downloads.remove(packet.transfer);
                              if (downloads.isEmpty()) {
                                  PeerServer.log().info("All downloads complete!");
                              }
                              PeerServer.log().fine(packet.peer().getForeignName() + ": received all bytes for " + packet.pathName() + ": file transfer successful");
                          } else {
                              packet.sendBytesRequest();
                          }
                      })
                      .ifErr(err -> {
                          cancelFile(packet);
                          PeerServer.log().warning(packet.peer().getForeignName() + ": error checking write status for " + packet.pathName() + ": " + err.getClass().getName() + ": " + err.getMessage());
                      });
        }
    }

    private void updateFile(FilePacket packet) {
        downloads.forEach(ft -> {
            if (ft.equals(packet.transfer)) {
                ft.updatePacket(packet);
            }
        });
    }

    public void cancelFile(FileDescriptor fd) {
        downloads.stream()
                 .filter(transfer -> transfer.fileDescriptor.equals(fd))
                 .findFirst()
                 .ifPresent(this::cancelFile);
    }

    private void cancelFile(FilePacket packet) {
        cancelFile(packet.transfer);
    }

    private void cancelFile(FileTransfer transfer) {
        PeerServer.fsManager().cancelFileLoader(transfer);

        if (!downloads.remove(transfer)) {
            PeerServer.log().warning("tried to remove " + transfer + " but was not found");
        } else {
            PeerServer.log().info("Removed download of " + transfer.pathName() + ".");
            if (downloads.isEmpty()) {
                PeerServer.log().info("All downloads complete!");
            }
        }
    }

    private void cancelPeerFiles(Peer peer) {
        Stream<FileTransfer> toRemove = downloads.stream().filter(ft -> ft.peer == peer);
        // Clear any file transfers associated with this peer
        toRemove.forEach(ft -> {
            downloads.remove(ft);
            PeerServer.fsManager().cancelFileLoader(ft)
                    .ifOk(res -> {
                        if (res) {
                            PeerServer.log().fine(ft.peer.getForeignName() + ": cancelling transfer of " + ft.pathName());
                        }
                    })
                    .ifErr(err -> PeerServer.log().warning(ft.peer.getForeignName() + ": failed cancelling file loader: "+ err.getMessage()));
        });
    }
}
