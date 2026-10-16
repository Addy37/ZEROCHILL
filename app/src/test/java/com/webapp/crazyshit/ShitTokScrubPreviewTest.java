package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Looper;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokScrubPreviewTest {
    final List<Bitmap> shown = new ArrayList<>();
    final ShitTokScrubPreview preview = new ShitTokScrubPreview(shown::add);
    int creates;
    boolean valid = true;
    final FakeExtractor extractor = new FakeExtractor();
    static class FakeExtractor implements ShitTokScrubPreview.Extractor {
        final List<Long> requests = new ArrayList<>();
        final List<SettableFuture<Bitmap>> results = new ArrayList<>();
        int closes;
        @Override public ListenableFuture<Bitmap> frame(long position) {
            requests.add(position);
            SettableFuture<Bitmap> future = SettableFuture.create();
            results.add(future);
            return future;
        }
        @Override public void close() { closes++; }
    }
    void begin(String key) { preview.begin(key, () -> { creates++; return extractor; }, () -> valid); }
    void idle(long millis) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }
    Bitmap bitmap() { return Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888); }
    Bitmap last() { return shown.get(shown.size() - 1); }
    @After public void close() { preview.end(); }

    @Test public void noExtractorUntilActiveDragRequestsPosition() {
        preview.request(1000); idle(200);
        assertEquals(0, creates);
        begin("A"); idle(200);
        assertEquals(0, creates);
        preview.request(1000); idle(0);
        assertEquals(1, creates);
        preview.end(); preview.request(5000); idle(200);
        assertEquals(1, extractor.requests.size());
    }
    @Test public void oneInFlightAndLatestTargetWinsWithoutQueueGrowth() {
        begin("A"); preview.request(1000); idle(0);
        for (int i = 2; i <= 90; i++) preview.request(i * 1000L);
        assertEquals(1, extractor.requests.size());
        extractor.results.get(0).set(bitmap()); idle(0);
        assertNull(last()); // The old target never flashes under the latest timestamp.
        idle(99); assertEquals(1, extractor.requests.size());
        idle(1); assertEquals(List.of(1000L, 90_000L), extractor.requests);
        Bitmap latest = bitmap(); extractor.results.get(1).set(latest); idle(0);
        assertSame(latest, last());
    }
    @Test public void scheduledMovesCoalesceBeforeCreation() {
        begin("A");
        preview.request(1000); preview.request(2000); preview.request(9000);
        idle(0); assertEquals(List.of(9000L), extractor.requests);
    }
    @Test public void cacheMissKeepsLastFrameUntilLatestCompletesButFailureClearsIt() {
        begin("A"); preview.request(1000); idle(0);
        Bitmap first = bitmap(); extractor.results.get(0).set(first); idle(0);
        preview.request(2000); idle(100);
        assertSame(first, last());
        preview.request(3000);
        extractor.results.get(1).set(bitmap()); idle(0);
        assertSame("superseded completion must not flash", first, last());
        idle(100);
        Bitmap latest = bitmap(); extractor.results.get(2).set(latest); idle(0);
        assertSame(latest, last());
        preview.request(4000); idle(100);
        assertSame(latest, last());
        extractor.results.get(3).setException(new IllegalStateException("offline")); idle(0);
        assertNull("failure must retain the time-only fallback", last());
    }
    @Test public void cacheIsSmallAndClearsAtEndOfDrag() {
        begin("A");
        for (int i = 0; i < 6; i++) {
            preview.request(i * 1000L); idle(100);
            extractor.results.get(i).set(bitmap()); idle(0);
        }
        Map<?, ?> cache = ReflectionHelpers.getField(preview, "cache");
        assertEquals(4, cache.size()); assertFalse(cache.containsKey(0L));
        preview.request(4000); idle(100);
        assertEquals(6, extractor.requests.size()); assertNotNull(last());
        preview.end(); assertEquals(0, cache.size()); assertNull(last());
    }
    @Test public void endCancelsAndRejectsAlreadyQueuedCompletionFromPreviousClip() {
        begin("A"); preview.request(1000); idle(0);
        extractor.results.get(0).set(bitmap()); // Completion is posted, then the holder changes.
        preview.end(); begin("B"); idle(0);
        assertNull(last()); assertEquals(1, extractor.closes);
        assertTrue(((Map<?, ?>) ReflectionHelpers.getField(preview, "cache")).isEmpty());
    }
    @Test public void endCancelsFutureAndPendingLatestRequest() {
        begin("A"); preview.request(1000); idle(0); preview.request(9000);
        preview.end(); idle(500);
        assertTrue(extractor.results.get(0).isCancelled());
        assertEquals(1, extractor.requests.size()); assertNull(last());
    }
    @Test public void identityGenerationOrLifecycleInvalidationRejectsFrame() {
        begin("A"); preview.request(1000); idle(0);
        valid = false; extractor.results.get(0).set(bitmap()); idle(0);
        assertNull(last()); assertEquals(1, extractor.closes);
        preview.request(3000); idle(200); assertEquals(1, extractor.requests.size());
    }
    @Test public void cancelledPreviewCannotOpenOrDeliverMoreMediaBytes() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean open = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        androidx.media3.datasource.DataSource upstream = new androidx.media3.datasource.DataSource() {
            @Override public void addTransferListener(androidx.media3.datasource.TransferListener listener) {}
            @Override public long open(androidx.media3.datasource.DataSpec spec) { return 100L; }
            @Override public int read(byte[] data, int offset, int length) {
                reads.incrementAndGet(); open.set(false); return 1;
            }
            @Override public android.net.Uri getUri() { return android.net.Uri.parse("https://example.invalid/clip.mp4"); }
            @Override public void close() {}
        };
        androidx.media3.datasource.DataSource guarded = ReflectionHelpers.callStaticMethod(ShitTokScrubPreview.class,
                "cancellableSource", ReflectionHelpers.ClassParameter.from(androidx.media3.datasource.DataSource.class, upstream),
                ReflectionHelpers.ClassParameter.from(java.util.concurrent.atomic.AtomicBoolean.class, open));
        byte[] buffer = new byte[8];
        assertThrows(java.io.InterruptedIOException.class, () -> guarded.read(buffer, 0, 8));
        assertEquals(1, reads.get());
        assertThrows(java.io.InterruptedIOException.class, () -> guarded.read(buffer, 0, 8));
        assertEquals(1, reads.get());
        assertThrows(java.io.InterruptedIOException.class, () -> guarded.open(
                new androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://example.invalid/clip.mp4"))));
    }

    @Test public void failureAndTimeoutKeepTimeOnlyWithoutRetryingSameSource() {
        begin("A"); preview.request(1000); idle(0);
        extractor.results.get(0).setException(new IllegalStateException("source unavailable")); idle(0);
        assertNull(last()); assertEquals(1, extractor.closes);
        preview.request(2000); idle(200); assertEquals(1, extractor.requests.size());
        begin("A"); preview.request(5000); idle(200); assertEquals(1, creates);
        begin("new URL"); preview.request(5000); idle(0); assertEquals(2, creates);
        idle(1500); assertTrue(extractor.results.get(1).isCancelled()); assertNull(last());
        preview.request(6000); idle(200); assertEquals(2, extractor.requests.size());
    }
}
