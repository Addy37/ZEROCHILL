package com.webapp.crazyshit;

import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ShitTokSourcePoolTest {
    private ExecutorService io;
    private AtomicLong clock;
    private ShitTokSourcePool pool;
    @Before public void setup() {
        io = Executors.newFixedThreadPool(3); clock = new AtomicLong();
        pool = new ShitTokSourcePool(io, clock::get);
        ShitTokCommunityPopularity.setScoresForTest(Collections.emptyMap());
    }
    @After public void cleanup() { pool.stop(); io.shutdownNow(); ShitTokCommunityPopularity.resetForTest(); }
    static NativeContentItem clip(String source, int n) {
        return new NativeContentItem("media", source + n, "https://" + source + ".invalid/" + n,
                "", "", source, "", "", "", 0, .5f);
    }
    private void awaitHealth(int source, ShitTokSourcePool.Health health) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (pool.health(source) != health && System.nanoTime() < end) Thread.sleep(1);
        assertEquals(health, pool.health(source));
    }
    @Test public void healthyContributesAndFailureRetriesLaterThenRecovers() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Callable<List<NativeContentItem>> loader = () -> {
            if (calls.incrementAndGet() == 1) throw new java.io.IOException();
            return Arrays.asList(clip("recovered", 1));
        };
        pool.request(8, true, true, loader); awaitHealth(8, ShitTokSourcePool.Health.FAILED);
        pool.request(8, true, true, loader); assertEquals(1, calls.get());
        pool.request(3, true, true, () -> Arrays.asList(clip("fast", 1)));
        awaitHealth(3, ShitTokSourcePool.Health.READY);
        assertEquals("fast1", pool.take(1).get(0).title);
        clock.set(5_000); pool.request(8, true, true, loader);
        awaitHealth(8, ShitTokSourcePool.Health.READY);
        assertEquals("recovered1", pool.take(1).get(0).title);
    }
    @Test public void emptyHasBoundedBackoffAndUnavailableAndFilteredDoNotFetch() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Callable<List<NativeContentItem>> empty = () -> { calls.incrementAndGet(); return Collections.emptyList(); };
        pool.request(1, true, true, empty); awaitHealth(1, ShitTokSourcePool.Health.EMPTY);
        for (int i = 0; i < 100; i++) pool.request(1, true, true, empty);
        assertEquals(1, calls.get()); clock.set(15_000); pool.request(1, true, true, empty);
        while (calls.get() != 2) Thread.yield(); awaitHealth(1, ShitTokSourcePool.Health.EMPTY);
        pool.request(2, false, true, empty); pool.request(3, true, false, empty);
        assertEquals(ShitTokSourcePool.Health.FILTERED, pool.health(2));
        assertEquals(ShitTokSourcePool.Health.UNAVAILABLE, pool.health(3)); assertEquals(2, calls.get());
    }
    @Test public void slowDoesNotBlockFastAndRetainsItsLateResultWithoutDuplicateStorm() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Callable<List<NativeContentItem>> slow = () -> {
            calls.incrementAndGet(); entered.countDown(); release.await(); return Arrays.asList(clip("slow", 1));
        };
        try {
            pool.request(8, true, true, slow); assertTrue(entered.await(2, TimeUnit.SECONDS));
            clock.set(6_000); assertEquals(ShitTokSourcePool.Health.SLOW, pool.health(8));
            for (int i = 0; i < 100; i++) pool.request(8, true, true, slow);
            pool.request(3, true, true, () -> Arrays.asList(clip("fast", 1)));
            awaitHealth(3, ShitTokSourcePool.Health.READY);
            assertEquals("fast1", pool.take(1).get(0).title); assertEquals(1, calls.get());
            release.countDown(); awaitHealth(8, ShitTokSourcePool.Health.READY);
            assertEquals("slow1", pool.take(1).get(0).title);
        } finally { release.countDown(); }
    }
    @Test public void staleUninterruptibleSourceCannotMutateNewGeneration() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1);
        pool.request(8, true, true, () -> {
            entered.countDown();
            while (release.getCount() > 0) try { release.await(); } catch (InterruptedException ignored) {}
            exited.countDown(); return Arrays.asList(clip("stale", 1));
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS)); pool.stop();
        pool.request(3, true, true, () -> Arrays.asList(clip("new", 1)));
        awaitHealth(3, ShitTokSourcePool.Health.READY); release.countDown(); assertTrue(exited.await(2, TimeUnit.SECONDS));
        io.submit(() -> {}).get(2, TimeUnit.SECONDS);
        List<NativeContentItem> result = pool.take(24);
        assertEquals(1, result.size()); assertEquals("new1", result.get(0).title);
    }
    @Test public void repeatedFastRefillCannotStarveReadyPeersAndStorageIsBounded() throws Exception {
        for (int source = 1; source <= 8; source++) {
            final int id = source;
            pool.request(id, true, true, () -> {
                List<NativeContentItem> many = new ArrayList<>();
                for (int i = 0; i < 100; i++) many.add(clip("source" + id, i)); return many;
            });
            awaitHealth(id, ShitTokSourcePool.Health.READY);
        }
        List<NativeContentItem> first = pool.take(8);
        assertEquals(8, first.stream().map(i -> i.uploader).distinct().count());
        assertEquals(88, pool.take(1000).size()); // 8 * 12 bounded raw slots.
    }
}
