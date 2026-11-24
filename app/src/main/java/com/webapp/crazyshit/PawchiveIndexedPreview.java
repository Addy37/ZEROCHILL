package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.Mp4Box;
import androidx.media3.extractor.GaplessInfoHolder;
import androidx.media3.extractor.mp4.BoxParser;
import androidx.media3.extractor.mp4.Track;
import androidx.media3.extractor.mp4.TrackSampleTable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Cold posters for ordinary AVC MP4: index and a bounded independent sample, decoded locally. */
@UnstableApi
final class PawchiveIndexedPreview {
    static final int PREFIX = 64 * 1024;
    static final int MAX_INDEX = 2 * 1024 * 1024;
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final int MAX_OPENS = 5;
    private static final int MAX_SAMPLE_COUNT = 250_000;
    private static final int MAX_SAMPLE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_BOXES = 2048;
    private static final int MAX_DEPTH = 8;

    interface Check { void run() throws IOException; }

    private PawchiveIndexedPreview() { }

    static Bitmap extract(Context context, String url, BunkrPreviewDataSource.Reader reader,
            Check check, Consumer<String> trace) throws Exception {
        if (!url.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            trace.accept("indexed unsupported container");
            return null;
        }
        Spans spans = new Spans(reader, check);
        byte[] index = locateIndex(spans);
        Mp4Box.ContainerBox moov = parseIndex(index);
        List<TrackSampleTable> tables = BoxParser.parseTraks(moov, new GaplessInfoHolder(),
                C.TIME_UNSET, null, true, false,
                track -> track != null && track.type == C.TRACK_TYPE_VIDEO ? track : null, false);
        if (tables.isEmpty()) throw new IOException("Missing video track");
        TrackSampleTable table = tables.get(0);
        Track track = table.track;
        Format format = track.format;
        if (!MimeTypes.VIDEO_H264.equals(format.sampleMimeType)
                || format.drmInitData != null || track.getSampleDescriptionEncryptionBox(0) != null
                || format.initializationData.isEmpty() || track.nalUnitLengthFieldLength < 1
                || track.nalUnitLengthFieldLength > 4) {
            trace.accept("indexed unsupported codec");
            return null;
        }
        if (format.width <= 0 || format.height <= 0 || format.width > 4096 || format.height > 4096)
            throw new IOException("Unsupported video dimensions");
        int i = table.getIndexOfLaterOrEqualSynchronizationSample(1_000_000L);
        if (i == C.INDEX_UNSET) i = table.getIndexOfEarlierOrEqualSynchronizationSample(1_000_000L);
        if (i == C.INDEX_UNSET) i = table.getIndexOfLaterOrEqualSynchronizationSample(0);
        if (i < 0 || i >= table.sampleCount || (table.flags[i] & C.BUFFER_FLAG_KEY_FRAME) == 0)
            throw new IOException("No independent video sample");
        long offset = table.offsets[i];
        int size = table.sizes[i];
        if (size <= 0 || size > MAX_SAMPLE_BYTES || offset < 0 || offset > spans.total - size)
            throw new IOException("Invalid sync sample");
        trace.accept("indexed AVC sample offset=" + offset + " size=" + size);
        byte[] sample = annexB(spans.read(offset, size), track.nalUnitLengthFieldLength);
        trace.accept("indexed reads=" + spans.opens + " bytes=" + spans.received);
        check.run();
        return decode(context, format, sample);
    }

    /** Skip media payloads by their declared box size; no scan through a large mdat. */
    static byte[] locateIndex(Spans spans) throws IOException {
        spans.read(0, PREFIX);
        long pos = 0;
        for (int i = 0; i < 64 && pos < spans.total; i++) {
            if (spans.total - pos < 8) throw new IOException("Truncated MP4 box");
            byte[] header = spans.header(pos);
            long length = u32(header, 0);
            int type = (int) u32(header, 4);
            int h = 8;
            if (length == 1) {
                if (header.length < 16) throw new IOException("Truncated extended box");
                length = ByteBuffer.wrap(header, 8, 8).getLong();
                h = 16;
            } else if (length == 0) length = spans.total - pos;
            if (length < h || length > spans.total - pos) throw new IOException("Invalid MP4 box");
            if (type == Mp4Box.TYPE_moov) {
                if (length > MAX_INDEX || h != 8) throw new IOException("Unsupported MP4 index size");
                return spans.read(pos, (int) length);
            }
            if (type == 0x6d6f6f66) throw new IOException("Fragmented MP4");
            pos += length;
        }
        throw new IOException("Missing MP4 index");
    }

    static final class Spans {
        final BunkrPreviewDataSource.Reader reader;
        final Check check;
        final List<Span> cached = new ArrayList<>();
        long total = -1;
        int opens, received;
        Spans(BunkrPreviewDataSource.Reader reader, Check check) {
            this.reader = reader; this.check = check;
        }
        byte[] header(long pos) throws IOException {
            int minimum = (int) Math.min(16, total - pos);
            for (Span span : cached) {
                if (pos >= span.start && pos + minimum <= span.start + span.bytes.length)
                    return read(pos, minimum);
            }
            return read(pos, (int) Math.min(PREFIX, total - pos));
        }
        byte[] read(long start, int size) throws IOException {
            check.run();
            if (start < 0 || size <= 0 || size > MAX_BYTES) throw new IOException("Invalid indexed range");
            if (total >= 0) {
                if (start >= total) throw new IOException("Indexed range outside file");
                size = (int) Math.min(size, total - start);
            }
            byte[] output = new byte[size];
            int done = 0;
            while (done < size) {
                check.run();
                long pos = start + done;
                Span hit = null;
                long next = start + size;
                for (Span span : cached) {
                    if (pos >= span.start && pos < span.start + span.bytes.length) {
                        hit = span; break;
                    }
                    if (span.start > pos) next = Math.min(next, span.start);
                }
                if (hit == null) {
                    int request = (int) Math.min(size - done, next - pos);
                    if (opens >= MAX_OPENS || request > MAX_BYTES - received)
                        throw new IOException("Indexed preview budget exhausted");
                    opens++;
                    BunkrPreviewDataSource.Block block = reader.read(pos, request);
                    if (block.total <= 0 || (total >= 0 && block.total != total)
                            || pos >= block.total || block.bytes.length != Math.min(request, block.total - pos))
                        throw new IOException("Invalid indexed response");
                    total = block.total;
                    received += block.bytes.length;
                    hit = new Span(pos, block.bytes);
                    cached.add(hit);
                    if (start + size > total) {
                        size = (int) (total - start);
                        output = Arrays.copyOf(output, size);
                    }
                }
                int from = (int) (pos - hit.start);
                int count = Math.min(size - done, hit.bytes.length - from);
                if (count <= 0) throw new IOException("Incomplete indexed range");
                System.arraycopy(hit.bytes, from, output, done, count);
                done += count;
            }
            return output;
        }
    }

    private static final class Span {
        final long start;
        final byte[] bytes;
        Span(long start, byte[] bytes) { this.start = start; this.bytes = bytes; }
    }

    private static long u32(byte[] b, int p) throws IOException {
        if (p < 0 || p > b.length - 4) throw new IOException("Truncated MP4 value");
        return ((long)(b[p] & 255) << 24) | ((long)(b[p+1] & 255) << 16)
                | ((long)(b[p+2] & 255) << 8) | (b[p+3] & 255);
    }

    static Mp4Box.ContainerBox parseIndex(byte[] bytes) throws IOException {
        if (bytes.length < 8 || bytes.length > MAX_INDEX || u32(bytes, 0) != bytes.length
                || (int) u32(bytes, 4) != Mp4Box.TYPE_moov) throw new IOException("Invalid MP4 index");
        Mp4Box.ContainerBox moov = new Mp4Box.ContainerBox(Mp4Box.TYPE_moov, bytes.length);
        parseChildren(moov, bytes, 8, bytes.length, 0, new int[1]);
        return moov;
    }

    private static void parseChildren(Mp4Box.ContainerBox parent, byte[] b, int start, int end,
            int depth, int[] count) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("MP4 index too deep");
        for (int pos = start; pos < end;) {
            if (end - pos < 8 || ++count[0] > MAX_BOXES) throw new IOException("Invalid MP4 children");
            long length = u32(b, pos);
            int type = (int) u32(b, pos + 4);
            if (length < 8 || length > end - pos) throw new IOException("Invalid MP4 child");
            int stop = pos + (int) length;
            if (type == 0x6d766578 || type == 0x73656e63 || type == 0x7361697a || type == 0x7361696f)
                throw new IOException("Unsupported fragmented or encrypted index");
            if (type == Mp4Box.TYPE_trak || type == Mp4Box.TYPE_mdia || type == Mp4Box.TYPE_minf
                    || type == Mp4Box.TYPE_stbl || type == Mp4Box.TYPE_edts) {
                Mp4Box.ContainerBox child = new Mp4Box.ContainerBox(type, stop);
                parseChildren(child, b, pos + 8, stop, depth + 1, count);
                parent.add(child);
            } else if (type == Mp4Box.TYPE_mvhd || type == Mp4Box.TYPE_tkhd || type == Mp4Box.TYPE_mdhd
                    || type == Mp4Box.TYPE_hdlr || type == Mp4Box.TYPE_stsd || type == Mp4Box.TYPE_stts
                    || type == Mp4Box.TYPE_stss || type == Mp4Box.TYPE_ctts || type == Mp4Box.TYPE_elst
                    || type == Mp4Box.TYPE_stsc || type == Mp4Box.TYPE_stsz || type == Mp4Box.TYPE_stz2
                    || type == Mp4Box.TYPE_stco || type == Mp4Box.TYPE_co64) {
                if (type == Mp4Box.TYPE_stsz || type == Mp4Box.TYPE_stz2) {
                    if (length < 20 || u32(b, pos + 16) > MAX_SAMPLE_COUNT)
                        throw new IOException("Too many MP4 samples");
                } else if (type == Mp4Box.TYPE_stsd) {
                    if (length < 16 || u32(b, pos + 12) != 1)
                        throw new IOException("Multiple MP4 sample descriptions");
                } else if (type == Mp4Box.TYPE_stts || type == Mp4Box.TYPE_stss
                        || type == Mp4Box.TYPE_ctts || type == Mp4Box.TYPE_stsc
                        || type == Mp4Box.TYPE_stco || type == Mp4Box.TYPE_co64 || type == Mp4Box.TYPE_elst) {
                    if (length < 16 || u32(b, pos + 12) > MAX_SAMPLE_COUNT)
                        throw new IOException("Invalid MP4 table count");
                }
                parent.add(new Mp4Box.LeafBox(type, new ParsableByteArray(Arrays.copyOfRange(b, pos, stop))));
            }
            pos = stop;
        }
    }

    static byte[] annexB(byte[] sample, int fieldLength) throws IOException {
        if (fieldLength < 1 || fieldLength > 4) throw new IOException("Invalid AVC length field");
        ByteArrayOutputStream output = new ByteArrayOutputStream(sample.length);
        boolean idr = false;
        for (int pos = 0; pos < sample.length;) {
            if (sample.length - pos < fieldLength) throw new IOException("Truncated AVC length");
            long n = 0;
            for (int j = 0; j < fieldLength; j++) n = (n << 8) | (sample[pos++] & 255);
            if (n <= 0 || n > sample.length - pos || output.size() + n + 4 > MAX_SAMPLE_BYTES)
                throw new IOException("Invalid AVC unit");
            if ((sample[pos] & 31) == 5) idr = true;
            output.write(new byte[] {0,0,0,1}, 0, 4);
            output.write(sample, pos, (int) n);
            pos += (int) n;
        }
        if (!idr) throw new IOException("Sync sample not an AVC IDR");
        return output.toByteArray();
    }

    private static Bitmap decode(Context context, Format format, byte[] sample) throws Exception {
        File tmp = File.createTempFile("pawchive-frame-", ".mp4", context.getCacheDir());
        try {
            MediaFormat media = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, format.width, format.height);
            for (int i = 0; i < format.initializationData.size(); i++)
                media.setByteBuffer("csd-" + i, ByteBuffer.wrap(format.initializationData.get(i)));
            MediaMuxer muxer = new MediaMuxer(tmp.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            try {
                if (format.rotationDegrees == 90 || format.rotationDegrees == 180 || format.rotationDegrees == 270)
                    muxer.setOrientationHint(format.rotationDegrees);
                int track = muxer.addTrack(media);
                muxer.start();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                info.set(0, sample.length, 0, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                muxer.writeSampleData(track, ByteBuffer.wrap(sample), info);
                // MP4 permits a zero-size EOS sample to set the final frame duration.
                info.set(0, 0, 1_000_000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                muxer.writeSampleData(track, ByteBuffer.allocate(0), info);
                muxer.stop();
            } finally { muxer.release(); }
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(tmp.getAbsolutePath());
                if (android.os.Build.VERSION.SDK_INT >= 27)
                    return retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            BunkrMediaPreview.SIZE, BunkrMediaPreview.SIZE);
                if ((long) format.width * format.height > 1920L * 1080L) return null;
                return retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } finally { retriever.release(); }
        } finally { tmp.delete(); }
    }
}
