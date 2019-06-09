package unimelb.bitbox.util.fs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class RandomAccessChannel implements AutoCloseable {
    private final RandomAccessFile raf;
    private final FileChannel channel;

    RandomAccessChannel(File file) throws IOException {
        raf = new RandomAccessFile(file, "rw");
        channel = raf.getChannel();
        channel.lock();
    }

    public void write(ByteBuffer src, long position) throws IOException {
        channel.write(src, position);
    }

    public int read(byte[] dest) throws IOException {
        return raf.read(dest);
    }

    void reset() throws IOException {
        raf.seek(0);
    }

    @Override
    public void close() throws IOException {
        if (channel == null || raf == null) {
            throw new IOException("RandomAccessChannel not initialised correctly");
        }
        channel.close();
        raf.close();
    }
}
