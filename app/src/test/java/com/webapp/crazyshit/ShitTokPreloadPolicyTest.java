package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;

public final class ShitTokPreloadPolicyTest {
    @Test
    public void playerWindow_preparesCurrentAndNextTwo() {
        int selected = 10;

        assertTrue(ChaosFeedView.shouldPreparePlayer(10, selected));
        assertTrue(ChaosFeedView.shouldPreparePlayer(11, selected));
        assertTrue(ChaosFeedView.shouldPreparePlayer(12, selected));
        assertFalse(ChaosFeedView.shouldPreparePlayer(9, selected));
        assertFalse(ChaosFeedView.shouldPreparePlayer(13, selected));
    }

    @Test
    public void clearDisplayGesture_usesIntentionalPinchThresholds() {
        assertTrue(ChaosFeedView.shouldEnterClearDisplay(0.78f));
        assertFalse(ChaosFeedView.shouldEnterClearDisplay(0.90f));
        assertTrue(ChaosFeedView.shouldExitClearDisplay(1.22f));
        assertFalse(ChaosFeedView.shouldExitClearDisplay(1.10f));
    }

    @Test
    public void startupQueue_seedsSixSwipeableItems() {
        assertEquals(6, ChaosStartupPreloader.STARTER_ITEMS);
    }

    @Test
    public void mediaCache_isCappedAtTwoHundredMiB() {
        assertEquals(200L * 1024L * 1024L, ShitTokMediaCache.MAX_CACHE_BYTES);
    }

    @Test
    public void offTabRefill_onlyWhenAppIsForegroundAndReservoirRunsLow() {
        assertTrue(ChaosFeedView.shouldWarmOffTab(true, 5));
        assertFalse(ChaosFeedView.shouldWarmOffTab(true, 10));
        assertFalse(ChaosFeedView.shouldWarmOffTab(false, 2));
        assertTrue(ChaosFeedView.hasReservoirRoom(23, 0));
        assertFalse(ChaosFeedView.hasReservoirRoom(24, 0));
        assertTrue(ChaosFeedView.hasReservoirRoom(30, 10));
    }

    @Test
    public void sessionUrlsRejectDuplicatesAfterRefreshAndAllowNewClips() {
        Set<String> session = new HashSet<>();
        Set<String> hidden = new HashSet<>();
        NativeContentItem watched = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "watched", "https://example.com/watched", "", "", "", "");
        NativeContentItem fresh = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "fresh", "https://example.com/fresh", "", "", "", "");
        assertTrue(ChaosFeedView.acceptUnique(watched, hidden, session));
        // Refresh resets the visible queue, but deliberately retains this same session set.
        assertFalse(ChaosFeedView.acceptUnique(watched, hidden, session));
        assertTrue(ChaosFeedView.acceptUnique(fresh, hidden, session));
        hidden.add("https://example.com/hidden");
        assertFalse(ChaosFeedView.acceptUnique(new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "hidden", "https://example.com/hidden",
                "", "", "", ""), hidden, session));
    }
}
