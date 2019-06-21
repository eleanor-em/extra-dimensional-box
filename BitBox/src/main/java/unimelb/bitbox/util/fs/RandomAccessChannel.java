package unimelb.bitbox.util.fs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Combines a RandomAccessFile with a FileChannel for ease of use.
 *
 * @author Eleanor McMurtry
 */
class RandomAccessChannel implements AutoCloseable {
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private boolean closed = false;

    RandomAccessChannel(File file) throws IOException {
        raf = new RandomAccessFile(file, "rw");
        channel = raf.getChannel();
        channel.lock();
    }

    public void write(ByteBuffer src, long position) throws IOException {
        if (closed) {
            throw new IOException("channel closed");
        }
        channel.write(src, position);
    }

    public int read(byte[] dest) throws IOException {
        if (closed) {
            throw new IOException("channel closed");
        }
        return raf.read(dest);
    }

    void reset() throws IOException {
        if (closed) {
            throw new IOException("channel closed");
        }
        raf.seek(0);
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            channel.close();
            raf.close();
            closed = true;
        }
    }
}
