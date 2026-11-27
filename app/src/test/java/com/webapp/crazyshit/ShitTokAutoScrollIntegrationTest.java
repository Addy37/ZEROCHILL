package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokAutoScrollIntegrationTest {
    private ChaosFeedView feed(Activity activity, int size) {
        ShitTokCategoryStrip.Tab.select(activity, ShitTokCategoryStrip.Tab.CHAOS);
        ChaosFeedView feed = new ChaosFeedView(activity, item -> { }, false);
        // Stop the constructor's asynchronous source request from altering the fake deck.
        ReflectionHelpers.setField(feed, "closed", true);
        List<NativeContentItem> items = ReflectionHelpers.getField(feed, "items");
        for (int i = 0; i < size; i++) {
            items.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    "Video " + i, "https://example.invalid/video/" + i,
                    "", "", "Kaotic", ""));
        }
        RecyclerView.Adapter<?> adapter = ReflectionHelpers.getField(feed, "adapter");
        adapter.notifyDataSetChanged();
        ReflectionHelpers.setField(feed, "active", true);
        activity.setContentView(feed);
        layoutFeed(feed);
        return feed;
    }

    private void layoutFeed(ChaosFeedView feed) {
        feed.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY));
        feed.layout(0, 0, 400, 800);
    }

    private void request(ChaosFeedView feed, int from) {
        ReflectionHelpers.callInstanceMethod(feed, "requestAutoAdvance",
                ReflectionHelpers.ClassParameter.from(int.class, from));
    }

    @Test public void duplicateAndStaleQueuedAdvanceAreCancelled() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        host.get().setTheme(R.style.Theme_CrazyShit);
        ChaosFeedView feed = feed(host.get(), 2);
        try {
            request(feed, 0);
            int generation = ReflectionHelpers.getField(feed, "autoAdvanceGeneration");
            request(feed, 0);
            assertEquals(generation, (int) ReflectionHelpers.getField(feed, "autoAdvanceGeneration"));
            assertTrue(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
            pager.setCurrentItem(1, false);
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            pager.setCurrentItem(0, false);
            assertEquals(0, (int) ReflectionHelpers.getField(feed, "selectedPosition"));
            request(feed, 0);
            ReflectionHelpers.callInstanceMethod(feed, "setAutoScrollEnabled",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            assertTrue((int) ReflectionHelpers.getField(feed, "autoAdvanceGeneration") > generation);
            // After A -> B -> A and a toggle, old posted callbacks cannot move the new A.
            assertEquals(-1, (int) ReflectionHelpers.getField(feed, "autoAdvanceIssuedFrom"));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, pager.getCurrentItem());
            feed.onHostPause();
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
        } finally {
            feed.close();
            host.pause().stop().destroy();
        }
    }

    @Test public void endedSelectedPlayerLoopsOffAndQueuesNextOn() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            f.selectedPlayer.state = Player.STATE_ENDED;
            ReflectionHelpers.callInstanceMethod(f.selected, "handleCompletion",
                    ReflectionHelpers.ClassParameter.from(ExoPlayer.class, f.selectedPlayer.player));
            assertEquals(1, f.selectedPlayer.seeks);
            assertEquals(1, f.selectedPlayer.plays);
            ReflectionHelpers.setField(f.selected, "userPaused", true);
            f.selectedPlayer.state = Player.STATE_ENDED;
            ReflectionHelpers.callInstanceMethod(f.selected, "handleCompletion",
                    ReflectionHelpers.ClassParameter.from(ExoPlayer.class, f.selectedPlayer.player));
            assertEquals(1, f.selectedPlayer.plays);
            ReflectionHelpers.setField(f.selected, "userPaused", false);
            ReflectionHelpers.callInstanceMethod(f.feed, "setAutoScrollEnabled",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            f.selectedPlayer.state = Player.STATE_ENDED;
            ReflectionHelpers.callInstanceMethod(f.selected, "handleCompletion",
                    ReflectionHelpers.ClassParameter.from(ExoPlayer.class, f.selectedPlayer.player));
            assertTrue(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
            assertEquals(1, f.selectedPlayer.plays);
            ReflectionHelpers.setField(f.feed, "closed", true);
            shadowOf(Looper.getMainLooper()).idle();
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            assertEquals(1, pager.getCurrentItem());
            assertFalse(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
        }
    }

    @Test public void turningOffPendingAutoScrollReplaysSelectedEndedClip() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            f.selectedPlayer.state = Player.STATE_ENDED;
            ReflectionHelpers.callInstanceMethod(f.feed, "setAutoScrollEnabled",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            request(f.feed, 0);
            assertTrue(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
            ReflectionHelpers.callInstanceMethod(f.feed, "setAutoScrollEnabled",
                    ReflectionHelpers.ClassParameter.from(boolean.class, false));
            assertFalse(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
            assertEquals(1, f.selectedPlayer.seeks);
            assertEquals(1, f.selectedPlayer.plays);
            ReflectionHelpers.setField(f.feed, "closed", true);
            shadowOf(Looper.getMainLooper()).idle();
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            assertEquals(0, pager.getCurrentItem());
        }
    }

    @Test public void togglingModeDoesNotCancelPendingFailedClipSkip() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        host.get().setTheme(R.style.Theme_CrazyShit);
        ChaosFeedView feed = feed(host.get(), 1);
        try {
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
            RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
            RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            ReflectionHelpers.setField(holder, "failurePending", true);
            request(feed, 0);
            assertTrue(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            for (boolean enabled : new boolean[] { true, false }) {
                ReflectionHelpers.callInstanceMethod(feed, "setAutoScrollEnabled",
                        ReflectionHelpers.ClassParameter.from(boolean.class, enabled));
                assertTrue(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            }
            List<NativeContentItem> items = ReflectionHelpers.getField(feed, "items");
            items.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    "Next video", "https://example.invalid/video/next", "", "", "Kaotic", ""));
            RecyclerView.Adapter<?> adapter = ReflectionHelpers.getField(feed, "adapter");
            adapter.notifyItemInserted(1);
            // Commit the inserted page before the posted advance may run.
            layoutFeed(feed);
            ReflectionHelpers.callInstanceMethod(feed, "tryPendingAutoAdvance");
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350));
            assertEquals(1, pager.getCurrentItem());
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
        } finally {
            feed.close();
            host.pause().stop().destroy();
        }
    }
    private void toggle(ChaosFeedView feed, boolean value) {
        ReflectionHelpers.callInstanceMethod(feed, "setAutoScrollEnabled",
                ReflectionHelpers.ClassParameter.from(boolean.class, value));
    }

    private void complete(Object holder, ShitTokAudioOwnershipTest.FakePlayer player) {
        player.state = Player.STATE_ENDED;
        ReflectionHelpers.callInstanceMethod(holder, "handleCompletion",
                ReflectionHelpers.ClassParameter.from(ExoPlayer.class, player.player));
    }

    @Test public void completedRetainedVideoRewindsBeforeReplayAndAdvancesOnlyAfterNewEnd() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            toggle(f.feed, true);
            complete(f.selected, f.selectedPlayer);
            shadowOf(Looper.getMainLooper()).idle();
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            assertEquals(1, pager.getCurrentItem());
            int seeks = f.selectedPlayer.seeks;
            pager.setCurrentItem(0, false);
            assertEquals(seeks + 1, f.selectedPlayer.seeks);
            assertEquals(0L, f.selectedPlayer.seekPosition);
            assertEquals(Player.STATE_READY, f.selectedPlayer.state);
            assertTrue(ReflectionHelpers.getField(f.feed, "autoScrollEnabled"));
            // Delayed duplicate from the old ending must not navigate while replay is READY.
            ReflectionHelpers.callInstanceMethod(f.selected, "handleCompletion",
                    ReflectionHelpers.ClassParameter.from(ExoPlayer.class, f.selectedPlayer.player));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, pager.getCurrentItem());
            complete(f.selected, f.selectedPlayer);
            complete(f.selected, f.selectedPlayer);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, pager.getCurrentItem());
        }
    }

    @Test public void oldPostedEndCannotAdvanceAfterRapidBackAndForwardVisits() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            toggle(f.feed, true);
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            Player.Listener listener = ReflectionHelpers.getField(f.selected, "uiPlayerListener");
            assertNotNull(listener);
            f.selectedPlayer.state = Player.STATE_ENDED;
            listener.onPlaybackStateChanged(Player.STATE_ENDED);
            ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState",
                    ReflectionHelpers.ClassParameter.from(int.class,
                            androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING));
            // UI work is deferred while dragging; the old end still has its original visit.
            shadowOf(Looper.getMainLooper()).idle();
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            for (int i = 0; i < 3; i++) {
                pager.setCurrentItem(1, false);
                pager.setCurrentItem(0, false);
            }
            assertEquals(Player.STATE_ENDED, f.selectedPlayer.state);
            ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState",
                    ReflectionHelpers.ClassParameter.from(int.class,
                            androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, pager.getCurrentItem());
            assertEquals(Player.STATE_READY, f.selectedPlayer.state);
            assertFalse(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
            f.selectedPlayer.state = Player.STATE_ENDED;
            listener.onPlaybackStateChanged(Player.STATE_ENDED);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, pager.getCurrentItem());
            assertTrue(ReflectionHelpers.getField(f.feed, "autoScrollEnabled"));
        }
    }

    @Test public void completedRetiredLeaseRestoresZeroWhilePartialWatchKeepsPosition() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(f.feed, "playbackOwner");
            NativeContentItem item = ReflectionHelpers.getField(f.selected, "item");
            f.selectedPlayer.state = Player.STATE_ENDED;
            owner.select("https://example.invalid/other", null, null, false);
            owner.select(item.url, null, null, false);
            CrazyShitRepository.StreamInfo stream = new CrazyShitRepository.StreamInfo(
                    "https://example.invalid/clip.mp4", item.url, "Fixture");
            owner.source(item.url, stream, owner.token(item.url));
            ShitTokAudioOwnershipTest.FakePlayer restored = owner.player(item.url) == f.selectedPlayer.player
                    ? f.selectedPlayer : f.offscreenPlayer;
            assertEquals(0L, restored.sourceStartPosition);
            restored.state = Player.STATE_READY;
            owner.select("https://example.invalid/other", null, null, false);
            owner.select(item.url, null, null, false);
            owner.source(item.url, stream, owner.token(item.url));
            restored = owner.player(item.url) == f.selectedPlayer.player ? f.selectedPlayer : f.offscreenPlayer;
            assertEquals(12_000L, restored.sourceStartPosition);
        }
    }

    @Test public void manualDragCancelsQueuedEndWithoutDisablingSavedAutoScroll() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            toggle(f.feed, true);
            complete(f.selected, f.selectedPlayer);
            ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState",
                    ReflectionHelpers.ClassParameter.from(int.class,
                            androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING));
            shadowOf(Looper.getMainLooper()).idle();
            assertFalse(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
            assertTrue(ReflectionHelpers.getField(f.feed, "autoScrollEnabled"));
            ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState",
                    ReflectionHelpers.ClassParameter.from(int.class,
                            androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0L, f.selectedPlayer.seekPosition);
            assertEquals(Player.STATE_READY, f.selectedPlayer.state);
            assertFalse(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
            assertTrue(f.host.get().getSharedPreferences("chaos_feed", Activity.MODE_PRIVATE)
                    .getBoolean("auto_scroll", false));
            assertEquals(0, ((androidx.viewpager2.widget.ViewPager2)
                    ReflectionHelpers.getField(f.feed, "pager")).getCurrentItem());
        }
    }
    @Test public void endDeliveredAfterReturningSelectionDuringDragCannotAdvanceReplay() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            toggle(f.feed, true);
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            Player.Listener listener = ReflectionHelpers.getField(f.selected, "uiPlayerListener");
            ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState",
                    ReflectionHelpers.ClassParameter.from(int.class,
                            androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING));
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            pager.setCurrentItem(1, false);
            pager.setCurrentItem(0, false);
            f.selectedPlayer.state = Player.STATE_ENDED;
            listener.onPlaybackStateChanged(Player.STATE_ENDED);
            shadowOf(Looper.getMainLooper()).idle();
            ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState",
                    ReflectionHelpers.ClassParameter.from(int.class,
                            androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, pager.getCurrentItem());
            assertEquals(0L, f.selectedPlayer.seekPosition);
            assertEquals(Player.STATE_READY, f.selectedPlayer.state);
            assertFalse(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
            assertTrue(ReflectionHelpers.getField(f.feed, "autoScrollEnabled"));
            f.selectedPlayer.state = Player.STATE_ENDED;
            listener.onPlaybackStateChanged(Player.STATE_ENDED);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, pager.getCurrentItem());
        }
    }
}
