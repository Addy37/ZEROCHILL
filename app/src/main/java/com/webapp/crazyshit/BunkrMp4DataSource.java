package com.webapp.crazyshit;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Repairs only bounded moov metadata presented to the player, above the unmodified byte cache. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class BunkrMp4DataSource implements DataSource {
    static final int MAX_MOOV_BYTES = 8 * 1024 * 1024;
    private final DataSource upstream;
    private final Set<Long> boxOffsets;
    private byte[] pending = new byte[0];
    private int pendingAt;
    private long position, payloadRemaining, upstreamRemaining;
    private boolean scanning;

    static Factory wrap(Factory upstream) {
        Set<Long> offsets = java.util.Collections.synchronizedSet(new HashSet<>());
        offsets.add(0L);
        return () -> new BunkrMp4DataSource(upstream.createDataSource(), offsets);
    }

    BunkrMp4DataSource(DataSource upstream, Set<Long> offsets) {
        this.upstream = upstream; this.boxOffsets = offsets;
    }

    @Override public long open(DataSpec spec) throws IOException {
        long length = upstream.open(spec);
        position = spec.position;
        upstreamRemaining = length;
        payloadRemaining = 0;
        pending = new byte[0]; pendingAt = 0;
        scanning = boxOffsets.contains(position);
        return length;
    }

    @Override public int read(byte[] target, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (pendingAt >= pending.length && scanning && payloadRemaining == 0) prepareBox();
        if (pendingAt < pending.length) {
            int count = Math.min(length, pending.length - pendingAt);
            System.arraycopy(pending, pendingAt, target, offset, count);
            pendingAt += count; position += count;
            return count;
        }
        int request = scanning ? (int) Math.min(length, payloadRemaining) : length;
        if (request == 0) return C.RESULT_END_OF_INPUT;
        int count = pull(target, offset, request);
        if (count > 0) { position += count; if (scanning) payloadRemaining -= count; }
        return count;
    }

    private void prepareBox() throws IOException {
        byte[] header = new byte[16];
        int count = fill(header, 0, 8);
        pendingAt = 0;
        if (count < 8) { pending = Arrays.copyOf(header, count); scanning = false; return; }
        long size = unsigned32(header, 0);
        int headerSize = 8;
        if (size == 1) {
            count += fill(header, 8, 8); headerSize = 16;
            if (count != 16 || (header[8] & 128) != 0) {
                pending = Arrays.copyOf(header, count); scanning = false; return;
            }
            size = unsigned32(header, 8) << 32 | unsigned32(header, 12);
        }
        pending = Arrays.copyOf(header, count);
        if (size < headerSize || size > Long.MAX_VALUE - position
                || !boxType(header) || upstreamRemaining != C.LENGTH_UNSET && size - headerSize > upstreamRemaining) {
            scanning = false; return;
        }
        if (boxOffsets.size() < 64) boxOffsets.add(position + size);
        payloadRemaining = size - headerSize;
        if (headerSize == 8 && size <= MAX_MOOV_BYTES && header[4] == 'm'
                && header[5] == 'o' && header[6] == 'o' && header[7] == 'v') {
            byte[] moov = new byte[(int) size];
            System.arraycopy(header, 0, moov, 0, headerSize);
            int bytes = fill(moov, headerSize, (int) payloadRemaining);
            payloadRemaining = 0;
            if (bytes + headerSize == size) BunkrMp4MetadataRepair.repair(moov);
            else { moov = Arrays.copyOf(moov, bytes + headerSize); scanning = false; }
            pending = moov;
        }
    }

    private int fill(byte[] data, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int count = pull(data, offset + total, length - total);
            if (count == C.RESULT_END_OF_INPUT) break;
            if (count == 0) break;
            total += count;
        }
        return total;
    }

    private int pull(byte[] data, int offset, int length) throws IOException {
        if (upstreamRemaining == 0) return C.RESULT_END_OF_INPUT;
        int wanted = upstreamRemaining == C.LENGTH_UNSET ? length
                : (int) Math.min(length, upstreamRemaining);
        int read = upstream.read(data, offset, wanted);
        if (read > 0 && upstreamRemaining != C.LENGTH_UNSET) upstreamRemaining -= read;
        return read;
    }

    private static boolean boxType(byte[] header) {
        for (int i = 4; i < 8; i++) if (header[i] < 32 || header[i] > 126) return false;
        return true;
    }
    private static long unsigned32(byte[] b, int at) {
        return ((long) (b[at] & 255) << 24) | ((long) (b[at + 1] & 255) << 16)
                | ((b[at + 2] & 255) << 8) | (b[at + 3] & 255);
    }
    @Override public Uri getUri() { return upstream.getUri(); }
    @Override public Map<String, List<String>> getResponseHeaders() { return upstream.getResponseHeaders(); }
    @Override public void addTransferListener(TransferListener listener) { upstream.addTransferListener(listener); }
    @Override public void close() throws IOException { pending = new byte[0]; pendingAt = 0; upstream.close(); }
}
