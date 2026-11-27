package com.webapp.crazyshit;

import android.app.Application;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSpec;
import androidx.media3.extractor.mp4.TrackSampleTable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PawchiveFramePreviewTest {
    @Test public void headAndLateMp4IndexShareOneByteBudgetAcrossExtractorReopens() throws Exception {
        long mediaSize = 203_846_822L;
        long index = 203_793_152L;
        int block = BunkrMediaPreview.PAWCHIVE_BLOCK_BYTES;
        List<Long> requests = new ArrayList<>();
        Uri uri = Uri.parse("https://n3.pawchive.pw/data/ab/cd/clip.mp4");
        try (BunkrPreviewDataSource bytes = new BunkrPreviewDataSource((position, size) -> {
            requests.add(position);
            return new BunkrPreviewDataSource.Block(new byte[(int) Math.min(size, mediaSize - position)], mediaSize);
        }, block)) {
            byte[] out = new byte[32];
            PawchiveFramePreview.BoundedSource head = new PawchiveFramePreview.BoundedSource(uri, bytes);
            assertEquals(mediaSize, head.open(new DataSpec(uri)));
            assertEquals(32, head.read(out, 0, out.length));
            head.close();

            PawchiveFramePreview.BoundedSource end = new PawchiveFramePreview.BoundedSource(uri, bytes);
            assertEquals(16, end.open(new DataSpec.Builder().setUri(uri).setPosition(index).setLength(16).build()));
            assertEquals(16, end.read(out, 0, out.length));
            assertEquals(C.RESULT_END_OF_INPUT, end.read(out, 0, out.length));
            end.close();

            assertEquals(mediaSize, head.open(new DataSpec(uri)));
            assertEquals(32, head.read(out, 0, out.length));
            head.close();
            assertEquals(Arrays.asList(0L, index - index % block), requests);
        }
    }
    @Test public void indexedReadsSkipLargeMdatAndReuseFetchedTail() throws Exception {
        int total = 10_000_000;
        int tail = total - 32;
        byte[] head = new byte[PawchiveIndexedPreview.PREFIX];
        java.nio.ByteBuffer.wrap(head).putInt(8).putInt(0x66747970)
                .putInt(tail - 8).putInt(0x6d646174);
        byte[] moov = new byte[32];
        java.nio.ByteBuffer.wrap(moov).putInt(32).putInt(0x6d6f6f76);
        List<Long> offsets = new ArrayList<>();
        PawchiveIndexedPreview.Spans spans = new PawchiveIndexedPreview.Spans((pos, size) -> {
            offsets.add(pos);
            if (pos == 0) return new BunkrPreviewDataSource.Block(head, total);
            if (pos == tail) return new BunkrPreviewDataSource.Block(moov, total);
            throw new AssertionError("Unexpected read " + pos);
        }, () -> {});
        assertArrayEquals(moov, PawchiveIndexedPreview.locateIndex(spans));
        assertEquals(Arrays.asList(0L, (long)tail), offsets);
        assertEquals(PawchiveIndexedPreview.PREFIX + 32, spans.received);
    }

    @Test public void indexedPreviewReusesEarlyKeyframeWhenPreferredFrameNeedsAnotherRequest()
            throws Exception {
        int prefix = PawchiveIndexedPreview.PREFIX;
        byte[] head = new byte[prefix];
        java.nio.ByteBuffer.wrap(head).putInt(8).putInt(0x66747970)
                .putInt(32).putInt(0x6d6f6f76);
        int total = 1_000_000;
        List<Long> requests = new ArrayList<>();
        PawchiveIndexedPreview.Spans spans = new PawchiveIndexedPreview.Spans((pos, size) -> {
            requests.add(pos);
            return new BunkrPreviewDataSource.Block(Arrays.copyOfRange(head, (int) pos, (int) pos + size), total);
        }, () -> {});
        assertEquals(32, PawchiveIndexedPreview.locateIndex(spans).length);

        int key = C.BUFFER_FLAG_KEY_FRAME;
        TrackSampleTable table = new TrackSampleTable(null,
                new long[]{128 * 1024, prefix - 16L, 500_000}, new int[]{1024, 32, 32}, 1024,
                new long[]{0, 500_000, 1_500_000}, new int[]{key, key, key},
                new int[]{0, 1, 2}, true, 2_000_000, 3);
        int chosen = PawchiveIndexedPreview.cachedAlternative(spans, table, 2);
        assertEquals(0, chosen);
        assertEquals(1024, spans.read(table.offsets[chosen], table.sizes[chosen]).length);
        assertEquals(java.util.Collections.singletonList(0L), requests);
        assertEquals(C.INDEX_UNSET, PawchiveIndexedPreview.cachedAlternative(spans, table, 0));
    }

    @Test public void avcSampleRequiresIdrAndConvertsLengthPrefixes() throws Exception {
        assertArrayEquals(new byte[] {0,0,0,1,0x65,0x12,0x34},
                PawchiveIndexedPreview.annexB(new byte[] {0,0,0,3,0x65,0x12,0x34}, 4));
        try {
            PawchiveIndexedPreview.annexB(new byte[] {0,0,0,1,0x41}, 4);
            fail("Non-IDR sample must be rejected");
        } catch (java.io.IOException expected) { }
    }
}
