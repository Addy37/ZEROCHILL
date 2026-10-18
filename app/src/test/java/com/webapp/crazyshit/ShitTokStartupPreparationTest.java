package com.webapp.crazyshit;

import android.app.Application;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokStartupPreparationTest {
    static class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void next() { tasks.removeFirst().run(); }
    }
    static NativeContentItem clip(String id) {
        return new NativeContentItem("media", id, "https://fixture.invalid/" + id, "", "", "", "");
    }
    static CrazyShitRepository.StreamInfo stream(NativeContentItem item) {
        return new CrazyShitRepository.StreamInfo(item.url + ".mp4", item.url, item.title);
    }

    @Test public void workStartsAtPublicationAndStopsOncePortraitIsFound() throws Exception {
        Queue worker = new Queue();
        AtomicInteger resolves = new AtomicInteger(), probes = new AtomicInteger();
        ShitTokStartupPreparation race = new ShitTokStartupPreparation(worker,
                item -> { resolves.incrementAndGet(); return stream(item); },
                (source, allowed) -> { probes.incrementAndGet(); return source.title.equals("portrait") ? .5f : 1.8f; }, () -> 0L);
        try {
            race.offer(clip("landscape")); race.offer(clip("portrait")); race.offer(clip("later"));
            assertEquals(0, resolves.get());
            worker.next();
            assertEquals(1.8f, race.await(clip("landscape").url).aspect, 0f);
            assertFalse(race.portraitReady(clip("landscape").url));
            worker.next(); worker.next();
            assertTrue(race.portraitReady(clip("portrait").url));
            assertFalse(race.await(clip("later").url).attempted);
            assertEquals(2, resolves.get()); assertEquals(2, probes.get());
        } finally { race.close(); }
    }

    @Test public void twoSlowSourcesLeaveThirdLaneAvailableToPortrait() throws Exception {
        CountDownLatch slow = new CountDownLatch(2), release = new CountDownLatch(1), portrait = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger(), probes = new AtomicInteger();
        ShitTokStartupPreparation race = new ShitTokStartupPreparation(Executors.newFixedThreadPool(3), item -> {
            int count = active.incrementAndGet(); peak.accumulateAndGet(count, Math::max);
            try {
                if (!item.title.equals("portrait")) { slow.countDown(); release.await(2, TimeUnit.SECONDS); }
                return stream(item);
            } finally { active.decrementAndGet(); }
        }, (source, allowed) -> { probes.incrementAndGet(); portrait.countDown(); return .5f; }, () -> 0L);
        try {
            race.offer(clip("slow-a")); race.offer(clip("slow-b"));
            assertTrue(slow.await(1, TimeUnit.SECONDS));
            race.offer(clip("portrait"));
            assertTrue("The third provider must finish before either slow provider", portrait.await(1, TimeUnit.SECONDS));
            assertTrue(race.portraitReady(clip("portrait").url) || race.await(clip("portrait").url).aspect == .5f);
            release.countDown();
            assertNull(race.await(clip("slow-a").url).aspect);
            assertNull(race.await(clip("slow-b").url).aspect);
            assertEquals(1, probes.get()); assertEquals(3, peak.get());
        } finally { release.countDown(); race.close(); }
    }

    @Test public void deferredProbeKeepsResolvedUrlForOwnerControlledAdmission() throws Exception {
        Queue worker = new Queue();
        ShitTokStartupPreparation race = new ShitTokStartupPreparation(worker,
                ShitTokStartupPreparationTest::stream,
                (source, allowed) -> { throw new ShitTokOrientationAdmission.Deferred(); }, () -> 0L);
        try {
            race.offer(clip("unknown")); worker.next();
            ShitTokStartupPreparation.Prepared result = race.await(clip("unknown").url);
            assertNotNull(result.stream); assertNull(result.aspect); assertTrue(result.attempted);
        } finally { race.close(); }
    }

    @Test public void preparationIsBoundedDeduplicatedAndDoesNotRestartAfterDeadline() throws Exception {
        Queue worker = new Queue(); AtomicLong clock = new AtomicLong();
        ShitTokStartupPreparation race = new ShitTokStartupPreparation(worker,
                item -> { fail("Expired speculative preparation must not resolve"); return null; },
                (source, allowed) -> { fail(); return 0f; }, clock::get);
        try {
            for (int i = 0; i < 20; i++) { race.offer(clip("clip" + i)); race.offer(clip("clip" + i)); }
            assertEquals(6, worker.tasks.size());
            clock.set(ShitTokStartupPreparation.BUDGET_MS);
            while (!worker.tasks.isEmpty()) worker.next();
            assertFalse(race.await(clip("clip0").url).attempted);
            assertNull(race.await(clip("clip7").url));
        } finally { race.close(); }
    }

    @Test public void streamHandoffIsSingleUseAndAspectOnlyMatchesExactMedia() throws Exception {
        Queue worker = new Queue();
        ShitTokStartupPreparation race = new ShitTokStartupPreparation(worker,
                ShitTokStartupPreparationTest::stream, (source, allowed) -> .5f, () -> 0L);
        try {
            NativeContentItem item = clip("one"); race.offer(item); worker.next();
            assertNotNull(race.take(item.url).stream);
            assertNull("Retry must be free to resolve a new URL", race.take(item.url));
            assertEquals(.5f, race.aspect(item.url, stream(item)), 0f);
            assertNull(race.aspect(item.url, new CrazyShitRepository.StreamInfo("https://fixture.invalid/changed.mp4", item.url, "changed")));
        } finally { race.close(); }
    }

    @Test public void closedPreparationCannotPublishOrRunQueuedWork() {
        Queue worker = new Queue();
        ShitTokStartupPreparation race = new ShitTokStartupPreparation(worker,
                item -> { fail(); return null; }, (source, allowed) -> 0f, () -> 0L);
        race.offer(clip("old")); race.close(); worker.next(); race.offer(clip("new"));
        assertTrue(worker.tasks.isEmpty()); assertFalse(race.portraitReady(clip("old").url));
    }
}
