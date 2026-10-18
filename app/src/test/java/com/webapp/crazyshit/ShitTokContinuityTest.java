package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.MotionEvent;
import androidx.media3.common.FlagSet;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.viewpager2.widget.ViewPager2;
import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

/** Real feed admission, paging gates and owner, with queued IO and decoder-free engines. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokContinuityTest {
    @Test public void seenAndHeartedCandidatesNeverConsumeRunwayEvenAfterDryBatches() {
        try (Fixture f = new Fixture()) {
            ShitTokSeenStore store = ReflectionHelpers.getField(f.feed, "seenStore");
            store.watch(item("seen").url, null);
            store.heart(item("liked").url, null);
            ReflectionHelpers.setField(f.feed, "consecutiveDryLoads", 3);
            f.candidates("seen", "liked", "fresh");
            call(f.feed, "loadMorePool"); f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(2, f.items.size());
            assertEquals("fresh", f.items.get(1).title);
            assertTrue(((java.util.concurrent.atomic.AtomicInteger) ReflectionHelpers.getField(f.feed, "seenRejected")).get() > 0);
            assertTrue(((java.util.concurrent.atomic.AtomicInteger) ReflectionHelpers.getField(f.feed, "likedRejected")).get() > 0);
        }
    }
    @Test public void fiveReadyClipsAndIncrementalAdmissionFillNormalTarget() {
        try (Fixture f = new Fixture()) {
            for (int i = 0; i < 5; i++) {
                NativeContentItem next = item("existing" + i);
                ReflectionHelpers.callInstanceMethod(f.feed, "appendUnique", from(List.class, Collections.singletonList(next)));
                f.cache.put(next.url, stream(next));
            }
            f.candidates("fresh1", "fresh2", "fresh3");
            call(f.feed, "maintainContinuity"); f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(6, (int) ReflectionHelpers.callInstanceMethod(f.feed, "usableUpcomingCount"));
            assertFalse((boolean) ReflectionHelpers.getField(f.feed, "continuityWorkNeeded"));
        }
    }
    @Test public void emptyTailDragRetainsCurrentSourceAndManualPause() {
        try (Fixture f = new Fixture()) {
            f.owner.setUserPaused(item("current").url, true);
            int prepares = f.engines.get(0).prepares, assignments = f.engines.get(0).sourceAssignments;
            f.candidates("next"); call(f.feed, "wakeForwardContinuity");
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(prepares, f.engines.get(0).prepares);
            assertEquals(assignments, f.engines.get(0).sourceAssignments);
            assertFalse(f.engines.get(0).play);
            assertEquals(0, (int) ReflectionHelpers.getField(f.feed, "selectedPosition"));
        }
    }
    static NativeContentItem item(String id) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, id,
                "https://fixture.invalid/" + id, "", "", "Kaotic", "", "", "", 0L, .5f);
    }
    static CrazyShitRepository.StreamInfo stream(NativeContentItem item) {
        return new CrazyShitRepository.StreamInfo(item.url + ".mp4", item.url, item.title);
    }
    static void call(Object target, String name) { ReflectionHelpers.callInstanceMethod(target, name); }
    static void state(ShitTokPlayerPoolTest.Engine engine, int value) {
        engine.state = value;
        for (Player.Listener listener : new ArrayList<>(engine.listeners)) {
            listener.onPlaybackStateChanged(value);
            listener.onEvents(engine.player, new Player.Events(new FlagSet.Builder()
                    .add(Player.EVENT_PLAYBACK_STATE_CHANGED).build()));
        }
    }
    static class QueuedIo extends AbstractExecutorService {
        final Deque<Runnable> tasks = new ArrayDeque<>();
        boolean stopped;
        public void execute(Runnable task) { tasks.addLast(task); }
        @Override public java.util.concurrent.Future<?> submit(Runnable task) {
            java.util.concurrent.FutureTask<Void> future = new java.util.concurrent.FutureTask<>(task, null);
            future.run();
            return future;
        }
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; tasks.clear(); return Collections.emptyList(); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long time, TimeUnit unit) { return stopped; }
    }
    static class Fixture implements AutoCloseable {
        final ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        final ChaosFeedView feed = new ChaosFeedView(host.get(), i -> {}, false);
        final QueuedIo io = new QueuedIo();
        final ArrayList<NativeContentItem> items = ReflectionHelpers.getField(feed, "items");
        final Map<String, CrazyShitRepository.StreamInfo> cache = ReflectionHelpers.getField(feed, "streamCache");
        final ShitTokPlaybackOwner owner = ReflectionHelpers.getField(feed, "playbackOwner");
        final List<ShitTokPlayerPoolTest.Engine> engines = new ArrayList<>();
        Fixture() {
            java.util.concurrent.ExecutorService original = ReflectionHelpers.getField(feed, "io");
            original.shutdownNow();
            ReflectionHelpers.setField(feed, "io", io);
            owner.setPlayerFactoryForTest(() -> {
                ShitTokPlayerPoolTest.Engine e = new ShitTokPlayerPoolTest.Engine(); engines.add(e); return e.player;
            });
            NativeContentItem current = item("current");
            // This fixture restores an already selected page. It is not a new discovery draw,
            // which now correctly excludes a page watched by an earlier fixture/session.
            items.add(current);
            ((Set<String>) ReflectionHelpers.getField(feed, "sessionUrls")).add(current.url);
            cache.put(current.url, stream(current));
            ReflectionHelpers.setField(feed, "active", true);
            owner.select(current.url, null, null, true);
            owner.permission(true, false);
            owner.source(current.url, stream(current), owner.token(current.url));
            owner.attach(current.url, new PlayerView(host.get()));
            owner.syncAudio();
            state(engines.get(0), Player.STATE_READY);
            state(engines.get(0), Player.STATE_BUFFERING);
            assertFalse(owner.allowsSpeculativeWork());
            ReflectionHelpers.setField(feed, "orientationWorkAllowed", false);
        }
        void candidates(String... names) {
            Deque<NativeContentItem> pending = ReflectionHelpers.getField(feed, "orientationPending");
            Map<String, CrazyShitRepository.StreamInfo> startup = ReflectionHelpers.getStaticField(ChaosStartupPreloader.class, "RESOLVED");
            for (String name : names) { NativeContentItem i = item(name); pending.add(i); startup.put(i.url, stream(i)); }
            ((ShitTokOrientationAdmission) ReflectionHelpers.getField(feed, "orientationAdmission")).close();
            ReflectionHelpers.setField(feed, "orientationAdmission", new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> 0L));
            ReflectionHelpers.setField(feed, "sourceRequestsEnabled", true);
        }
        public void close() {
            feed.close();
            Map<String, CrazyShitRepository.StreamInfo> startup = ReflectionHelpers.getStaticField(ChaosStartupPreloader.class, "RESOLVED");
            startup.clear();
            host.close();
        }
    }
    @Test public void discardedRecentCandidateDoesNotClaimFirstFeedAdmission() {
        try (Fixture f = new Fixture()) {
            Set<String> recent = ReflectionHelpers.getField(f.feed, "recentSet");
            recent.add(item("watched").url);
            f.candidates("watched");
            call(f.feed, "loadMorePool");
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1, f.items.size());
            assertFalse((boolean) ReflectionHelpers.getField(f.feed, "firstAdmitted"));
            f.candidates("fresh");
            call(f.feed, "loadMorePool");
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(2, f.items.size());
            assertTrue((boolean) ReflectionHelpers.getField(f.feed, "firstAdmitted"));
        }
    }
    @Test public void starvingThinPagerBuildsSixResolvedNextPagesWithoutExtraPlayers() {
        for (int readyAhead = 0; readyAhead <= 1; readyAhead++) try (Fixture f = new Fixture()) {
            if (readyAhead == 1) {
                NativeContentItem next = item("existing");
                ReflectionHelpers.callInstanceMethod(f.feed, "appendUnique", from(List.class, Collections.singletonList(next)));
                f.cache.put(next.url, stream(next));
            }
            f.candidates("one", "two", "three", "four", "five", "six", "deep");
            call(f.feed, "requestContinuity"); call(f.feed, "maintainContinuity");
            assertEquals(1, f.io.tasks.size());
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(6, (int) ReflectionHelpers.callInstanceMethod(f.feed, "usableUpcomingCount"));
            assertEquals(7, f.items.size());
            assertFalse(f.owner.allowsSpeculativeWork());
            assertEquals(0, f.engines.stream().skip(1).mapToInt(e -> e.prepares).sum());
            call(f.feed, "loadMorePool");
            assertTrue(f.io.tasks.isEmpty());
            assertFalse(((Deque<?>) ReflectionHelpers.getField(f.feed, "orientationPending")).isEmpty());
        }
    }
    @Test public void actualUpwardDragWakesAtPagerEndButSmallOrBackwardMotionDoesNot() {
        try (Fixture f = new Fixture()) {
            f.candidates("one", "two");
            ReflectionHelpers.setField(f.feed, "consecutiveDryLoads", 4);
            ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            assertEquals(ViewPager2.SCROLL_STATE_IDLE, pager.getScrollState());
            touch(f.feed, MotionEvent.ACTION_DOWN, 150, 350);
            touch(f.feed, MotionEvent.ACTION_MOVE, 150, 349);
            assertTrue(f.io.tasks.isEmpty());
            touch(f.feed, MotionEvent.ACTION_MOVE, 150, 450);
            assertTrue(f.io.tasks.isEmpty());
            touch(f.feed, MotionEvent.ACTION_MOVE, 150, 150);
            assertTrue(ReflectionHelpers.getField(f.feed, "forwardSwipeIntent"));
            assertEquals(1, f.io.tasks.size());
            assertFalse(f.owner.allowsSpeculativeWork());
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(2, (int) ReflectionHelpers.callInstanceMethod(f.feed, "usableUpcomingCount"));
        }
        assertFalse(ChaosFeedView.isForwardContinuityDrag(100, -50, 16));
    }
    @Test public void rejectedContinuityCandidateDoesNotStrandThePager() {
        try (Fixture f = new Fixture()) {
            f.candidates("one", "two", "three", "deep");
            java.util.concurrent.atomic.AtomicInteger probes = new java.util.concurrent.atomic.AtomicInteger();
            ReflectionHelpers.setField(f.feed, "orientationAdmission", new ShitTokOrientationAdmission((s, allowed) -> {
                if (probes.incrementAndGet() == 1) throw new java.io.IOException("candidate failed");
                return .5f;
            }, () -> 0L));
            call(f.feed, "maintainContinuity");
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(4, probes.get());
            assertEquals(3, (int) ReflectionHelpers.callInstanceMethod(f.feed, "usableUpcomingCount"));
            assertEquals(4, f.items.size());
            assertFalse(f.owner.allowsSpeculativeWork());
        }
    }
    @Test public void dragCanFetchContinuityButAdapterMutationWaitsForIdle() {
        try (Fixture f = new Fixture()) {
            f.candidates("one", "two");
            ReflectionHelpers.setField(f.feed, "userPaging", true);
            call(f.feed, "wakeForwardContinuity");
            assertEquals(1, f.io.tasks.size());
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1, f.items.size());
            assertFalse(((Deque<?>) ReflectionHelpers.getField(f.feed, "deferredUiApplies")).isEmpty());
            ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState", from(int.class, ViewPager2.SCROLL_STATE_IDLE));
            assertEquals(3, f.items.size());
            assertEquals(2, (int) ReflectionHelpers.callInstanceMethod(f.feed, "usableUpcomingCount"));
        }
    }
    @Test public void unresolvedOrFailedPagesDoNotCountAsRunwayAndFailedPageIsRemoved() {
        try (Fixture f = new Fixture()) {
            NativeContentItem bad = item("bad"), good = item("good");
            ReflectionHelpers.callInstanceMethod(f.feed, "appendUnique", from(List.class, Arrays.asList(bad, good)));
            f.cache.put(good.url, stream(good));
            assertEquals(0, (int) ReflectionHelpers.callInstanceMethod(f.feed, "usableUpcomingCount"));
            Set<String> failed = ReflectionHelpers.getField(f.feed, "unplayable"); failed.add(bad.url);
            call(f.feed, "maintainContinuity");
            assertEquals(good.url, f.items.get(1).url);
            assertEquals(1, (int) ReflectionHelpers.callInstanceMethod(f.feed, "usableUpcomingCount"));
            Set<String> session = ReflectionHelpers.getField(f.feed, "sessionUrls");
            assertTrue(session.contains(bad.url));
        }
    }
    @Test public void leavingOrPausingPreventsContinuityAdmissionAndStaleGenerationCannotPublish() {
        try (Fixture f = new Fixture()) {
            f.candidates("one"); call(f.feed, "loadMorePool");
            ReflectionHelpers.setField(f.feed, "feedGeneration", 1);
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1, f.items.size());
            ReflectionHelpers.setField(f.feed, "active", false);
            assertFalse((boolean) ReflectionHelpers.callInstanceMethod(f.feed, "mayVerifyOrientation"));
            ReflectionHelpers.setField(f.feed, "active", true);
            ReflectionHelpers.setField(f.feed, "hostResumed", false);
            assertFalse((boolean) ReflectionHelpers.callInstanceMethod(f.feed, "mayVerifyOrientation"));
        }
    }
    @Test public void dryBatchesRetryWithoutLeavingAndHiddenFeedCancelsRecovery() {
        try (Fixture f = new Fixture()) {
            f.host.get().setContentView(f.feed); // View.postDelayed runs only after attachment.
            ReflectionHelpers.setField(f.feed, "consecutiveDryLoads", 4);
            call(f.feed, "maintainContinuity");
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6));
            assertEquals(0, (int)ReflectionHelpers.getField(f.feed, "consecutiveDryLoads"));
            ReflectionHelpers.setField(f.feed, "consecutiveDryLoads", 4);
            call(f.feed, "maintainContinuity");
            f.feed.setActive(false);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6));
            assertEquals(4, (int)ReflectionHelpers.getField(f.feed, "consecutiveDryLoads"));
        }
    }

    @Test public void coldFeedDoesNotHarvestBeforeInitialUiDrawAndReturnKeepsQueue() {
        try (Fixture f = new Fixture()) {
            f.candidates("starter", "next");
            ReflectionHelpers.setField(f.feed, "initialUiDrawn", false);
            call(f.feed, "loadMorePool");
            assertTrue(f.io.tasks.isEmpty());
            ReflectionHelpers.setField(f.feed, "initialUiDrawn", true);
            call(f.feed, "loadMorePool");
            assertEquals(1, f.io.tasks.size());
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            int retained = f.items.size();
            f.feed.setActive(false); f.feed.setActive(true);
            assertEquals(retained, f.items.size());
            assertTrue((boolean)ReflectionHelpers.getField(f.feed, "initialUiDrawn"));
        }
    }

    @Test public void rapidSelectionsRaisePressureBeforeTailAndDecayAfterPause() {
        try (Fixture f = new Fixture()) {
            ShitTokRunwayPolicy policy = ReflectionHelpers.getField(f.feed, "runway");
            long now = android.os.SystemClock.elapsedRealtime();
            policy.forward(now); policy.forward(now + 50);
            assertEquals(10, (int) ReflectionHelpers.callInstanceMethod(f.feed, "desiredRunway"));
            f.candidates("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
            ReflectionHelpers.setField(f.feed, "userPaging", true);
            call(f.feed, "maintainContinuity");
            assertEquals(1, f.io.tasks.size());
            f.io.tasks.removeFirst().run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1, f.items.size());
            ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState", from(int.class, ViewPager2.SCROLL_STATE_IDLE));
            assertEquals(10, (int) ReflectionHelpers.callInstanceMethod(f.feed, "usableUpcomingCount"));
            assertEquals(0, f.engines.stream().skip(1).mapToInt(e -> e.prepares).sum());
        }
    }

    private static void touch(ChaosFeedView feed, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 10L, action, x, y, 0);
        try { feed.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
}
