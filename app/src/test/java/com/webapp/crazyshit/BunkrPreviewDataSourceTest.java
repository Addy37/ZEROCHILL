package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrPreviewDataSourceTest {
    @Test public void randomReadsShareBlocksAndExposeLengthAndEof() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (BunkrPreviewDataSource source = new BunkrPreviewDataSource((at, size) -> {
            calls.incrementAndGet();
            byte[] bytes = new byte[(int) Math.min(size, 600_000 - at)];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (at + i);
            return new BunkrPreviewDataSource.Block(bytes, 600_000);
        })) {
            byte[] buffer = new byte[100];
            assertEquals(-1, source.getSize());
            assertEquals(100, source.readAt(12, buffer, 0, 100));
            assertEquals(12, buffer[0]);
            assertEquals(600_000, source.getSize());
            assertEquals(100, source.readAt(99, buffer, 0, 100));
            assertEquals(1, calls.get());
            assertEquals(10, source.readAt(599_990, buffer, 0, 100));
            assertEquals(2, calls.get());
            assertEquals(-1, source.readAt(600_000, buffer, 0, 100));
            assertEquals(0, source.readAt(0, buffer, 0, 0));
        }
    }

    @Test public void byteBudgetStopsDecoderFromReadingWholeLargeVideo() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (BunkrPreviewDataSource source = new BunkrPreviewDataSource((at, size) -> {
            calls.incrementAndGet();
            return new BunkrPreviewDataSource.Block(new byte[size], 1_000_000_000L);
        })) {
            byte[] buffer = new byte[1];
            int blocks = BunkrPreviewDataSource.MAX_BYTES / BunkrPreviewDataSource.BLOCK_BYTES;
            for (int i = 0; i < blocks; i++)
                assertEquals(1, source.readAt((long) i * BunkrPreviewDataSource.BLOCK_BYTES, buffer, 0, 1));
            assertThrows(IOException.class, () -> source.readAt(900_000_000L, buffer, 0, 1));
            assertEquals(blocks, calls.get());
        }
    }

    @Test public void malformedAndChangingRangesFailClosed() throws Exception {
        try (BunkrPreviewDataSource source = new BunkrPreviewDataSource((at, size) ->
                new BunkrPreviewDataSource.Block(new byte[size], 4))) {
            assertThrows(IOException.class, () -> source.readAt(0, new byte[1], 0, 1));
        }
        AtomicInteger calls = new AtomicInteger();
        try (BunkrPreviewDataSource source = new BunkrPreviewDataSource((at, size) ->
                new BunkrPreviewDataSource.Block(new byte[size], 900_000 + calls.incrementAndGet()))) {
            source.readAt(0, new byte[1], 0, 1);
            assertThrows(IOException.class, () -> source.readAt(300_000, new byte[1], 0, 1));
        }
    }

    @Test public void closeAndInvalidReadsNeverStartNetworkWork() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        BunkrPreviewDataSource source = new BunkrPreviewDataSource((at, size) -> {
            calls.incrementAndGet(); return new BunkrPreviewDataSource.Block(new byte[0], 0);
        });
        assertThrows(IOException.class, () -> source.readAt(-1, new byte[1], 0, 1));
        assertThrows(IOException.class, () -> source.readAt(0, new byte[1], 1, 1));
        source.close();
        assertThrows(IOException.class, () -> source.readAt(0, new byte[1], 0, 1));
        assertEquals(0, calls.get());
    }
}
