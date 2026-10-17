package com.webapp.crazyshit;

import android.app.Application;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ChaosStartupPreloaderTest {
    private Object lock;
    private NativeContentItem clip(String id) {
        return new NativeContentItem("media", id, "https://fixture.invalid/" + id, "", "", "", "");
    }
    private void set(String key, Object value) {
        ReflectionHelpers.setStaticField(ChaosStartupPreloader.class, key, value);
    }
    @Before public void reset() {
        lock = ReflectionHelpers.getStaticField(ChaosStartupPreloader.class, "LOCK");
        synchronized (lock) {
            for (String field : new String[]{"READY", "published"}) {
                Collection<?> items = ReflectionHelpers.getStaticField(ChaosStartupPreloader.class, field);
                items.clear();
            }
            Map<?, ?> resolved = ReflectionHelpers.getStaticField(ChaosStartupPreloader.class, "RESOLVED");
            resolved.clear();
            set("started", true); set("finished", false); set("claimed", false);
            set("deadline", System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
            set("resolvingUrl", "unused");
            // Prevent real network work when the fixture publishes listing results.
            set("resolution", new FutureTask<CrazyShitRepository.StreamInfo>(() -> null));
        }
    }
    @After public void cleanup() {
        synchronized (lock) {
            set("finished", true); set("started", false); set("claimed", false);
            set("resolution", null); set("resolvingUrl", null);
            lock.notifyAll();
        }
    }
    private void publish(NativeContentItem... items) {
        ReflectionHelpers.callStaticMethod(ChaosStartupPreloader.class, "publish",
                ReflectionHelpers.ClassParameter.from(android.content.Context.class, RuntimeEnvironment.getApplication()),
                ReflectionHelpers.ClassParameter.from(List.class, Arrays.asList(items)));
    }
    @Test public void onlyDefaultAndShitTokLaunchesPreload() {
        assertTrue(SplashActivity.shouldPreload(false, null));
        assertTrue(SplashActivity.shouldPreload(false, AppShortcuts.ACTION_CHAOS));
        assertFalse(SplashActivity.shouldPreload(true, null));
        assertFalse(SplashActivity.shouldPreload(true, AppShortcuts.ACTION_CHAOS));
        for (String action : new String[]{AppShortcuts.ACTION_SEARCH, AppShortcuts.ACTION_CONTINUE,
                AppShortcuts.ACTION_WATCH_LATER}) assertFalse(SplashActivity.shouldPreload(false, action));
    }
    @Test public void feedConsumesInFlightSplashWorkWithoutAnotherRace() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch early = new CountDownLatch(1);
        try {
            Future<List<NativeContentItem>> feed = worker.submit(() -> ChaosStartupPreloader.collect(
                    RuntimeEnvironment.getApplication(), batch -> early.countDown()));
            publish(clip("first"), clip("second"));
            assertTrue("first candidates must transfer before the race finishes", early.await(1, TimeUnit.SECONDS));
            assertFalse(feed.isDone());
            publish(clip("third"));
            synchronized (lock) { set("finished", true); lock.notifyAll(); }
            assertEquals(3, feed.get(1, TimeUnit.SECONDS).size());
            assertTrue(ChaosStartupPreloader.collect(RuntimeEnvironment.getApplication(), null).isEmpty());
        } finally { worker.shutdownNow(); }
    }
    @Test public void expiredRaceReturnsWithoutWaitingForItsNetworkWorker() throws Exception {
        set("deadline", System.nanoTime() - 1);
        assertTrue(ChaosStartupPreloader.collect(RuntimeEnvironment.getApplication(), null).isEmpty());
        assertFalse((boolean) ReflectionHelpers.getStaticField(ChaosStartupPreloader.class, "finished"));
    }
    @Test public void failedOrEmptyRaceDoesNotBlockFeed() throws Exception {
        set("finished", true);
        assertTrue(ChaosStartupPreloader.collect(RuntimeEnvironment.getApplication(), null).isEmpty());
    }
    @Test public void feedJoinsExactlyTheResolverStartedBySplash() throws Exception {
        NativeContentItem item = clip("same");
        CrazyShitRepository.StreamInfo stream = new CrazyShitRepository.StreamInfo("https://fixture.invalid/a.mp4", item.url, "a");
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        FutureTask<CrazyShitRepository.StreamInfo> task = new FutureTask<>(() -> { calls.incrementAndGet(); return stream; });
        set("resolvingUrl", item.url); set("resolution", task);
        task.run();
        assertSame(stream, ChaosStartupPreloader.resolveForFeed(RuntimeEnvironment.getApplication(), item));
        assertSame(stream, ChaosStartupPreloader.resolveForFeed(RuntimeEnvironment.getApplication(), item));
        assertEquals(1, calls.get());
    }
    @Test public void handoffReservesSpaceForOtherSourceFallbacks() throws Exception {
        publish(clip("a"), clip("b"), clip("c"));
        publish(clip("d"), clip("e"), clip("f"));
        publish(clip("g"), clip("h"), clip("i"));
        set("finished", true);
        assertEquals(6, ChaosStartupPreloader.collect(RuntimeEnvironment.getApplication(), null).size());
    }
}
