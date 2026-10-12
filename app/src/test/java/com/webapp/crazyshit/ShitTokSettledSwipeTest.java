package com.webapp.crazyshit;

import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import android.app.Application;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

/** Real holder/cache entry points, with decoder-free players and no source requests. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi")
public class ShitTokSettledSwipeTest {
    private CrazyShitRepository.StreamInfo cacheSelected(ShitTokAudioOwnershipTest.Fixture f) {
        // Fixture.close() shuts down source IO before reopening only the playback gates.
        ReflectionHelpers.setField(f.feed, "poolLoading", true);
        List<NativeContentItem> items = ReflectionHelpers.getField(f.feed, "items");
        CrazyShitRepository.StreamInfo stream = new CrazyShitRepository.StreamInfo(
                "https://example.invalid/clip.mp4", items.get(0).url, "Fixture");
        Map<String, CrazyShitRepository.StreamInfo> cache = ReflectionHelpers.getField(f.feed, "streamCache");
        cache.put(items.get(0).url, stream);
        ReflectionHelpers.setField(f.selected, "stream", stream);
        return stream;
    }

    private void state(ShitTokAudioOwnershipTest.Fixture f, int state) {
        ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState", from(int.class, state));
    }

    @Test public void draggingAndSettlingSilenceAllWithoutReadingHistory() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            state(f, ViewPager2.SCROLL_STATE_DRAGGING);
            state(f, ViewPager2.SCROLL_STATE_SETTLING);
            for (ShitTokAudioOwnershipTest.FakePlayer p : new ShitTokAudioOwnershipTest.FakePlayer[] {
                    f.selectedPlayer, f.offscreenPlayer, f.duplicatePlayer}) {
                assertFalse(p.readyToPlay);
                assertEquals(0f, p.volume, 0f);
                assertEquals(0, p.positionReads);
            }
        }
    }

    @Test public void cachedPreparationAndDelayedResumeWaitUntilIdle() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            CrazyShitRepository.StreamInfo stream = cacheSelected(f);
            state(f, ViewPager2.SCROLL_STATE_SETTLING);
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            ReflectionHelpers.callInstanceMethod(f.feed, "prepareVisible", from(int.class, 0));
            ReflectionHelpers.callInstanceMethod(f.selected, "prepare",
                    from(CrazyShitRepository.StreamInfo.class, stream), from(boolean.class, true));
            ReflectionHelpers.callInstanceMethod(f.selected, "playOwnedPlayer");
            assertEquals(0, f.selectedPlayer.plays);
            assertSame(f.selectedPlayer.player, ReflectionHelpers.getField(f.selected, "player"));
            state(f, ViewPager2.SCROLL_STATE_IDLE);
            assertEquals(1, f.selectedPlayer.plays);
            assertEquals(1f, f.selectedPlayer.volume, 0f);
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
        }
    }

    @Test public void uncachedResolutionDoesNotStartWhilePaging() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            state(f, ViewPager2.SCROLL_STATE_DRAGGING);
            ReflectionHelpers.callInstanceMethod(f.feed, "resolveAt", from(int.class, 0));
            ReflectionHelpers.callInstanceMethod(f.feed, "resolveAt", from(int.class, 1));
            Set<String> resolving = ReflectionHelpers.getField(f.feed, "resolving");
            assertTrue(resolving.isEmpty());
        }
    }

    @Test public void canceledSwipeRetainsExplicitPause() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            cacheSelected(f);
            ReflectionHelpers.setField(f.selected, "userPaused", true);
            state(f, ViewPager2.SCROLL_STATE_DRAGGING);
            state(f, ViewPager2.SCROLL_STATE_IDLE);
            assertTrue(ReflectionHelpers.getField(f.selected, "userPaused"));
            assertEquals(0, f.selectedPlayer.plays);
        }
    }

    @Test public void repeatedDirectionChangesCannotResumeUntilIdle() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            cacheSelected(f);
            for (int i = 0; i < 5; i++) {
                state(f, ViewPager2.SCROLL_STATE_DRAGGING);
                state(f, ViewPager2.SCROLL_STATE_SETTLING);
                ReflectionHelpers.callInstanceMethod(f.selected, "playOwnedPlayer");
                assertEquals(0, f.selectedPlayer.plays);
            }
            state(f, ViewPager2.SCROLL_STATE_IDLE);
            assertEquals(1, f.selectedPlayer.plays);
        }
    }

    @Test public void backgroundDuringSwipeCannotRestartOnIdle() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            cacheSelected(f);
            state(f, ViewPager2.SCROLL_STATE_DRAGGING);
            ReflectionHelpers.setField(f.feed, "hostResumed", false);
            state(f, ViewPager2.SCROLL_STATE_IDLE);
            assertEquals(0, f.selectedPlayer.plays);
            assertEquals(0f, f.selectedPlayer.volume, 0f);
        }
    }

    @Test public void seenBookkeepingWaitsForTheSettledVideo() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            cacheSelected(f);
            state(f, ViewPager2.SCROLL_STATE_DRAGGING);
            ReflectionHelpers.setField(f.feed, "settledSelectionPending", true);
            ReflectionHelpers.callInstanceMethod(f.feed, "settleSelectedPage");
            Set<String> seen = ReflectionHelpers.getField(f.feed, "recentSet");
            assertTrue(seen.isEmpty());
            state(f, ViewPager2.SCROLL_STATE_IDLE);
            assertEquals(1, seen.size());
            assertTrue(seen.contains("https://example.invalid/clip-0"));
            assertFalse(ReflectionHelpers.getField(f.feed, "settledSelectionPending"));
        }
    }

    @Test public void programmaticSettlingDoesNotBecomeUserNavigation() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            cacheSelected(f);
            state(f, ViewPager2.SCROLL_STATE_SETTLING);
            assertTrue(ReflectionHelpers.getField(f.feed, "userPaging"));
            assertFalse(ReflectionHelpers.getField(f.feed, "userDragInProgress"));
            state(f, ViewPager2.SCROLL_STATE_DRAGGING);
            state(f, ViewPager2.SCROLL_STATE_SETTLING);
            assertTrue(ReflectionHelpers.getField(f.feed, "userDragInProgress"));
            state(f, ViewPager2.SCROLL_STATE_IDLE);
            assertFalse(ReflectionHelpers.getField(f.feed, "userDragInProgress"));
        }
    }
}
