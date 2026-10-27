package com.webapp.crazyshit;

import static org.junit.Assert.*;
import androidx.media3.extractor.AacUtil;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.Test;

public class BunkrMp4MetadataRepairTest {
    // Exact esds and mp4a entry header from Bunkr file 15329122. It has no tag-05 ASC.
    static final String ESDS = "0000002c6573647300000000038080801b000200048080800d40150000000000efc70000efc7068080800102";
    static final String ENTRY = "000000506d703461000000000000000100000000000000000002001000000000bb800000";

    @Test public void missingAacConfigIsRepairedWithoutChangingBoxOrSampleOffsets() throws Exception {
        byte[] original = hex(ESDS);
        byte[] repaired = BunkrMp4MetadataRepair.repairedEsds(original, 48000, 2);
        assertNotNull(repaired); assertEquals(original.length, repaired.length);
        assertArrayEquals(hex(ESDS), original);
        byte[] asc = decoderSpecificInfo(repaired);
        assertArrayEquals(new byte[]{0x11, (byte) 0x90}, asc);
        AacUtil.Config config = AacUtil.parseAudioSpecificConfig(asc);
        assertEquals(48000, config.sampleRateHz); assertEquals(2, config.channelCount);
        assertEquals("mp4a.40.2", config.codecs);
        assertNull(BunkrMp4MetadataRepair.repairedEsds(repaired, 48000, 2));
    }

    @Test public void reproductionMatchesThePlayersOriginalOneByteAudioConfigCrash() {
        // Without tag 05, Media3 treats the following SL descriptor (tag 06, payload 02)
        // as decoder-specific data, then attempts to read sampling frequency from one byte.
        assertThrows(RuntimeException.class, () -> AacUtil.parseAudioSpecificConfig(new byte[]{2}));
    }

    @Test public void fullMoovTraversalChangesOnlyTheBrokenAudioEntry() {
        byte[] moov = fixture();
        byte[] before = moov.clone();
        assertTrue(BunkrMp4MetadataRepair.repair(moov));
        assertEquals(before.length, moov.length);
        int esds = find(moov, new byte[]{'e','s','d','s'}) - 4;
        for (int i = 0; i < moov.length; i++) {
            if (i < esds || i >= esds + hex(ESDS).length) assertEquals(before[i], moov[i]);
        }
        assertFalse(BunkrMp4MetadataRepair.repair(moov));
    }

    @Test public void unrelatedAndUnrepairableMetadataRemainsUntouched() {
        byte[] before = hex(ESDS);
        assertNull(BunkrMp4MetadataRepair.repairedEsds(before, 12345, 2));
        assertNull(BunkrMp4MetadataRepair.repairedEsds(before, 48000, 7));
        byte[] other = before.clone(); other[25] = 0x20;
        assertNull(BunkrMp4MetadataRepair.repairedEsds(other, 48000, 2));
        for (int cut = 0; cut < before.length; cut++) {
            assertNull(BunkrMp4MetadataRepair.repairedEsds(Arrays.copyOf(before, cut), 48000, 2));
        }
        assertArrayEquals(hex(ESDS), before);
    }

    @Test public void validConfigStaysByteForByteIntactEvenWithDifferentSampleEntryRate() {
        byte[] valid = BunkrMp4MetadataRepair.repairedEsds(hex(ESDS), 48000, 2);
        byte[] before = valid.clone();
        assertNull(BunkrMp4MetadataRepair.repairedEsds(valid, 44100, 1));
        assertArrayEquals(before, valid);
    }

    static byte[] fixture() {
        byte[] entry = concat(hex(ENTRY), hex(ESDS));
        return box("moov", box("trak", box("mdia", box("minf", box("stbl",
                box("stsd", concat(new byte[]{0,0,0,0,0,0,0,1}, entry)))))));
    }
    static byte[] decoderSpecificInfo(byte[] box) {
        int[] es = descriptor(box, 12);
        int[] dec = descriptor(box, es[0] + 3);
        int at = dec[0] + 13;
        assertEquals(5, box[at]);
        int[] asc = descriptor(box, at);
        return Arrays.copyOfRange(box, asc[0], asc[1]);
    }
    private static int[] descriptor(byte[] data, int at) {
        at++; int size = 0, v;
        do { v = data[at++] & 255; size = (size << 7) | (v & 127); } while ((v & 128) != 0);
        return new int[]{at, at + size};
    }
    static byte[] box(String type, byte[] body) {
        int size = body.length + 8;
        return concat(new byte[]{(byte)(size>>24),(byte)(size>>16),(byte)(size>>8),(byte)size},
                type.getBytes(java.nio.charset.StandardCharsets.US_ASCII), body);
    }
    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p,0,p.length); return out.toByteArray();
    }
    static byte[] hex(String s) {
        byte[] data = new byte[s.length()/2];
        for (int i=0;i<data.length;i++) data[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return data;
    }
    static int find(byte[] bytes, byte[] pattern) {
        for(int i=0;i<=bytes.length-pattern.length;i++) {
            boolean match=true; for(int j=0;j<pattern.length;j++) if(bytes[i+j]!=pattern[j]){match=false;break;}
            if(match)return i;
        }
        return -1;
    }
}
