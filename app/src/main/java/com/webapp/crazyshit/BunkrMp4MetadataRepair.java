package com.webapp.crazyshit;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** In-memory compatibility for MP4 AAC entries with missing/truncated decoder configuration. */
final class BunkrMp4MetadataRepair {
    private static final int[] AAC_RATES = {96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350};

    private BunkrMp4MetadataRepair() { }

    static boolean repair(byte[] moov) {
        if (moov.length < 8 || u32(moov, 0) != moov.length || !type(moov, 4, "moov")) return false;
        return children(moov, 8, moov.length, 0);
    }

    private static boolean children(byte[] data, int start, int end, int depth) {
        if (depth > 8) return false;
        boolean changed = false;
        for (int at = start; at <= end - 8;) {
            long rawSize = u32(data, at);
            if (rawSize < 8 || rawSize > end - at) break;
            int next = at + (int) rawSize;
            if (type(data, at + 4, "stsd")) {
                if (at + 16 <= next) changed |= sampleEntries(data, at + 16, next);
            } else if (type(data, at + 4, "trak") || type(data, at + 4, "mdia")
                    || type(data, at + 4, "minf") || type(data, at + 4, "stbl")) {
                changed |= children(data, at + 8, next, depth + 1);
            }
            at = next;
        }
        return changed;
    }

    private static boolean sampleEntries(byte[] data, int start, int end) {
        boolean changed = false;
        for (int at = start; at <= end - 8;) {
            long size = u32(data, at);
            if (size < 8 || size > end - at) break;
            int next = at + (int) size;
            if (type(data, at + 4, "mp4a") && size >= 36) {
                int version = u16(data, at + 16);
                int child = at + (version == 0 ? 36 : version == 1 ? 52 : (int) size);
                int channels = u16(data, at + 24);
                int rate = (int) (u32(data, at + 32) >>> 16);
                for (; child <= next - 8;) {
                    long childSize = u32(data, child);
                    if (childSize < 8 || childSize > next - child) break;
                    if (type(data, child + 4, "esds")) {
                        byte[] replacement = repairedEsds(Arrays.copyOfRange(data, child,
                                child + (int) childSize), rate, channels);
                        if (replacement != null) {
                            System.arraycopy(replacement, 0, data, child, replacement.length);
                            changed = true;
                        }
                    }
                    child += (int) childSize;
                }
            }
            at = next;
        }
        return changed;
    }

    static byte[] repairedEsds(byte[] original, int rate, int channels) {
        if (original.length < 16 || original.length > 65536 || u32(original, 0) != original.length || !type(original, 4, "esds")) return null;
        try {
            Descriptor es = descriptor(original, 12, original.length, 3);
            if (es.end - es.body < 3) return null;
            int flags = original[es.body + 2] & 255;
            int configAt = es.body + 3;
            if ((flags & 128) != 0) configAt += 2;
            if ((flags & 64) != 0) { if (configAt >= es.end) return null; configAt += 1 + (original[configAt] & 255); }
            if ((flags & 32) != 0) configAt += 2;
            Descriptor decoder = descriptor(original, configAt, es.end, 4);
            if (decoder.end - decoder.body < 13 || (original[decoder.body] & 255) != 0x40) return null;
            int specificAt = decoder.body + 13;
            Descriptor specific = specificAt == decoder.end ? null
                    : descriptor(original, specificAt, decoder.end, 5);
            // A complete existing ASC belongs to the source. Leave every such entry unchanged.
            if (specific != null && specific.end - specific.body >= 2) return null;
            byte[] asc = aacLcConfig(rate, channels);
            if (asc == null) return null;
            if (specific != null && (specific.end - specific.body != 1
                    || original[specific.body] != asc[0])) return null;
            byte[] esPrefix = Arrays.copyOfRange(original, es.body, configAt);
            byte[] decoderPrefix = Arrays.copyOfRange(original, decoder.body, specificAt);
            byte[] decoderTail = specific == null ? new byte[0]
                    : Arrays.copyOfRange(original, specific.end, decoder.end);
            byte[] esTail = Arrays.copyOfRange(original, decoder.end, es.end);
            byte[] boxTail = Arrays.copyOfRange(original, es.end, original.length);
            // Reuse redundant expandable-size encoding space. Never change file/sample offsets.
            for (int esWidth = 1; esWidth <= 4; esWidth++) {
                for (int decoderWidth = 1; decoderWidth <= 4; decoderWidth++) {
                    for (int specificWidth = 1; specificWidth <= 4; specificWidth++) {
                        byte[] dsi = encode(5, asc, specificWidth);
                        byte[] dec = encode(4, concat(decoderPrefix, dsi, decoderTail), decoderWidth);
                        byte[] rebuilt = concat(Arrays.copyOfRange(original, 8, 12),
                                encode(3, concat(esPrefix, dec, esTail), esWidth), boxTail);
                        int length = 8 + rebuilt.length;
                        int spare = original.length - length;
                        if (spare != 0 && spare < 8) continue;
                        byte[] result = new byte[original.length];
                        put32(result, 0, length);
                        System.arraycopy(original, 4, result, 4, 4);
                        System.arraycopy(rebuilt, 0, result, 8, rebuilt.length);
                        if (spare > 0) {
                            put32(result, length, spare);
                            result[length + 4] = 'f'; result[length + 5] = 'r';
                            result[length + 6] = 'e'; result[length + 7] = 'e';
                        }
                        return result;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    private static byte[] aacLcConfig(int rate, int channels) {
        int channelConfig = channels == 8 ? 7 : channels;
        if (channelConfig < 1 || channelConfig > 6 && channels != 8) return null;
        for (int i = 0; i < AAC_RATES.length; i++) if (AAC_RATES[i] == rate) {
            int value = (2 << 11) | (i << 7) | (channelConfig << 3);
            return new byte[]{(byte) (value >> 8), (byte) value};
        }
        return null;
    }

    private static Descriptor descriptor(byte[] data, int at, int end, int expectedTag) {
        if (at >= end || (data[at++] & 255) != expectedTag) throw new IllegalArgumentException();
        int size = 0;
        for (int i = 0; i < 4 && at < end; i++) {
            int value = data[at++] & 255;
            size = (size << 7) | (value & 127);
            if ((value & 128) == 0) {
                if (size > end - at) throw new IllegalArgumentException();
                return new Descriptor(at, at + size);
            }
        }
        throw new IllegalArgumentException();
    }

    private static byte[] encode(int tag, byte[] payload, int width) {
        int min = 1;
        while (min < 4 && payload.length >>> (min * 7) != 0) min++;
        width = Math.max(width, min);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(tag);
        for (int i = width - 1; i >= 0; i--)
            out.write(((payload.length >>> (i * 7)) & 127) | (i == 0 ? 0 : 128));
        out.write(payload, 0, payload.length); return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.write(part, 0, part.length);
        return out.toByteArray();
    }
    private static boolean type(byte[] data, int at, String type) {
        return at >= 0 && at + 4 <= data.length && data[at] == type.charAt(0)
                && data[at + 1] == type.charAt(1) && data[at + 2] == type.charAt(2)
                && data[at + 3] == type.charAt(3);
    }
    private static int u16(byte[] data, int at) { return (data[at] & 255) << 8 | data[at + 1] & 255; }
    private static long u32(byte[] data, int at) {
        return (long) u16(data, at) << 16 | u16(data, at + 2);
    }
    private static void put32(byte[] data, int at, int value) {
        for (int i = 3; i >= 0; i--) { data[at + i] = (byte) value; value >>>= 8; }
    }
    private static final class Descriptor {
        final int body, end;
        Descriptor(int body, int end) { this.body = body; this.end = end; }
    }
}
