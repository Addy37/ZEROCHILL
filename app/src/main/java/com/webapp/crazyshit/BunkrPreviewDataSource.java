package com.webapp.crazyshit;

import android.media.MediaDataSource;
import java.io.IOException;
import java.util.LinkedHashMap;

/** Random access for frame extraction without allowing a gallery tile to download a video. */
final class BunkrPreviewDataSource extends MediaDataSource {
    static final int BLOCK_BYTES = 256 * 1024;
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final int MAX_REQUESTS = 48;

    interface Reader {
        Block read(long position, int size) throws IOException;
    }
    static final class Block {
        final byte[] bytes;
        final long total;
        Block(byte[] bytes, long total) { this.bytes = bytes; this.total = total; }
    }

    private final Reader reader;
    private final LinkedHashMap<Long, byte[]> blocks = new LinkedHashMap<>(4, .75f, true);
    private long length = -1;
    private int received, requests;
    private volatile boolean closed;

    BunkrPreviewDataSource(Reader reader) { this.reader = reader; }

    @Override public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (closed || Thread.currentThread().isInterrupted()) throw new IOException("Preview canceled");
        if (position < 0 || offset < 0 || size < 0 || offset > buffer.length - size)
            throw new IOException("Invalid preview read");
        if (size == 0) return 0;
        if (length >= 0 && position >= length) return -1;
        long start = position - position % BLOCK_BYTES;
        byte[] bytes = blocks.get(start);
        if (bytes == null) {
            if (requests >= MAX_REQUESTS || received >= MAX_BYTES)
                throw new IOException("Preview budget exhausted");
            requests++;
            Block block = reader.read(start, Math.min(BLOCK_BYTES, MAX_BYTES - received));
            if (block.bytes.length > BLOCK_BYTES || block.bytes.length > MAX_BYTES - received
                    || block.total < 0 || start > block.total
                    || block.bytes.length > block.total - start)
                throw new IOException("Invalid preview range");
            if (length >= 0 && length != block.total) throw new IOException("Media changed");
            length = block.total;
            bytes = block.bytes;
            received += bytes.length;
            blocks.put(start, bytes);
            while (blocks.size() > 4) blocks.remove(blocks.keySet().iterator().next());
        }
        int within = (int) (position - start);
        if (within >= bytes.length) return -1;
        int count = Math.min(size, bytes.length - within);
        System.arraycopy(bytes, within, buffer, offset, count);
        return count;
    }

    @Override public long getSize() { return length; }
    @Override public void close() { closed = true; }
}
