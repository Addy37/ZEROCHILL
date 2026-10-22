package com.webapp.crazyshit;

import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class ShitTokPreferenceBufferTest {
    private NativeContentItem clip(String id) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, id,
                "https://example.com/" + id + ".mp4", "", "", "", "");
    }

    @Test public void slowPreferenceSurvivesOrdinaryHarvestAndRetainsOtherProviders() throws Exception {
        ExecutorService io = Executors.newSingleThreadExecutor();
        ShitTokPreferenceBuffer buffer = new ShitTokPreferenceBuffer(io, () -> 0L);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), ready = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try {
            Callable<List<NativeContentItem>> lookup = () -> {
                calls.incrementAndGet(); started.countDown(); release.await();
                return Arrays.asList(clip("match1"), clip("match2"), clip("excess"));
            };
            buffer.request(ContentPreference.MEN, lookup, ready::countDown);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            // Fast providers have already filled the normal runway and left a reservoir.
            List<NativeContentItem> normal = Arrays.asList(clip("kaotic"), clip("fapello"),
                    clip("regular"), clip("baddiehub"), clip("efukt"), clip("ordinaryHaven"));
            buffer.request(ContentPreference.MEN, lookup, ready::countDown);
            assertFalse(buffer.hasReady(ContentPreference.MEN));
            release.countDown(); assertTrue(ready.await(2, TimeUnit.SECONDS));
            buffer.request(ContentPreference.MEN, lookup, ready::countDown);
            List<NativeContentItem> matching = buffer.take(ContentPreference.MEN);
            assertEquals(1, calls.get()); assertEquals(2, matching.size());
            ArrayList<NativeContentItem> queue = new ArrayList<>(normal); queue.addAll(matching);
            List<NativeContentItem> ordered = ShitTokPreferenceMix.order(queue, matching::contains);
            assertEquals(8, ordered.size()); assertTrue(ordered.containsAll(normal));
            assertSame(matching.get(0), ordered.get(0)); assertSame(matching.get(1), ordered.get(3));
            assertFalse(buffer.hasReady(ContentPreference.MEN));
        } finally { release.countDown(); buffer.stop(); io.shutdownNow(); }
    }

    @Test public void navigationCancelsAndStaleWorkerCannotPublishIntoNewMode() throws Exception {
        ExecutorService io = Executors.newSingleThreadExecutor();
        ShitTokPreferenceBuffer buffer = new ShitTokPreferenceBuffer(io, () -> 0L);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), ready = new CountDownLatch(1);
        AtomicInteger staleCallbacks = new AtomicInteger();
        try {
            buffer.request(ContentPreference.MEN, () -> {
                started.countDown();
                // Model socket IO that finishes after cancellation.
                while (true) { try { release.await(); break; } catch (InterruptedException ignored) {} }
                return Collections.singletonList(clip("old"));
            }, staleCallbacks::incrementAndGet);
            assertTrue(started.await(2, TimeUnit.SECONDS)); buffer.stop();
            buffer.request(ContentPreference.WOMEN, () -> Collections.singletonList(clip("new")), ready::countDown);
            release.countDown(); assertTrue(ready.await(2, TimeUnit.SECONDS));
            assertEquals(0, staleCallbacks.get()); assertFalse(buffer.hasReady(ContentPreference.MEN));
            assertEquals("new", buffer.take(ContentPreference.WOMEN).get(0).title);
        } finally { release.countDown(); buffer.stop(); io.shutdownNow(); }
    }

    @Test public void allContentDoesNotFetchAndEmptyResultsBackOffBeforeRetry() throws Exception {
        ExecutorService io = Executors.newSingleThreadExecutor();
        AtomicLong clock = new AtomicLong(); AtomicInteger calls = new AtomicInteger();
        ShitTokPreferenceBuffer buffer = new ShitTokPreferenceBuffer(io, clock::get);
        Callable<List<NativeContentItem>> lookup = () -> { calls.incrementAndGet(); return Collections.emptyList(); };
        try {
            buffer.request(ContentPreference.ALL, lookup, () -> fail("All content cannot use preference worker"));
            io.submit(() -> {}).get(2, TimeUnit.SECONDS); assertEquals(0, calls.get());
            buffer.request(ContentPreference.MEN, lookup, () -> {});
            io.submit(() -> {}).get(2, TimeUnit.SECONDS); assertEquals(1, calls.get());
            buffer.request(ContentPreference.MEN, lookup, () -> {});
            io.submit(() -> {}).get(2, TimeUnit.SECONDS); assertEquals(1, calls.get());
            clock.set(30_001L); buffer.request(ContentPreference.MEN, lookup, () -> {});
            io.submit(() -> {}).get(2, TimeUnit.SECONDS); assertEquals(2, calls.get());
        } finally { buffer.stop(); io.shutdownNow(); }
    }
}
