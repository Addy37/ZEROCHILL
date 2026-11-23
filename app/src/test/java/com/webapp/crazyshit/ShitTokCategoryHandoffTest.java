package com.webapp.crazyshit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** No network or player needed: category handoff must never select a stale or unresolved page. */
public class ShitTokCategoryHandoffTest {
    private static NativeContentItem clip(String id) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, id,
                "https://example.test/" + id, "", "", "Test", "", "", "", 0L, .5f);
    }

    @Test public void keepsOutgoingPageUntilNewCategoryHasPlayableStream() {
        NativeContentItem old = clip("old");
        NativeContentItem next = clip("next");
        List<NativeContentItem> queue = Arrays.asList(old, next);
        assertEquals(-1, ChaosFeedView.firstReadyCategoryIndex(queue, 0,
                item -> item == next, item -> false));
        assertEquals(1, ChaosFeedView.firstReadyCategoryIndex(queue, 0,
                item -> item == next, item -> item == next));
    }

    @Test public void skipsStaleAndUnreadyPagesAfterRapidPreferenceChanges() {
        NativeContentItem old = clip("old");
        NativeContentItem abandoned = clip("abandoned-ladies");
        NativeContentItem buffering = clip("buffering-guys");
        NativeContentItem ready = clip("ready-guys");
        List<NativeContentItem> queue = Arrays.asList(old, abandoned, buffering, ready);
        assertEquals(3, ChaosFeedView.firstReadyCategoryIndex(queue, 0,
                item -> item == buffering || item == ready,
                item -> item == abandoned || item == ready));
        assertEquals(-1, ChaosFeedView.firstReadyCategoryIndex(queue, 0,
                item -> item == buffering || item == ready,
                item -> item == abandoned));
    }

    @Test public void remembersLadiesClipEarlierInQueueInsteadOfChoosingNewOne() {
        NativeContentItem ladiesSaved = clip("ladies-original");
        NativeContentItem chaos = clip("chaos-current");
        NativeContentItem ladiesNew = clip("ladies-new");
        List<NativeContentItem> queue = Arrays.asList(ladiesSaved, chaos, ladiesNew);
        assertEquals(0, ChaosFeedView.savedCategoryReturnIndex(queue, ladiesSaved.url,
                item -> item == ladiesSaved || item == ladiesNew));
        assertEquals(2, ChaosFeedView.firstReadyCategoryIndex(queue, 1,
                item -> item == ladiesSaved || item == ladiesNew,
                item -> true));
        // The saved bookmark takes priority even when another eligible clip is
        // immediately ready after the Chaos page.
    }

    @Test public void currentAndLaterBookmarksAreValidButWrongCategoryIsNot() {
        NativeContentItem ladies = clip("ladies");
        NativeContentItem chaos = clip("chaos");
        NativeContentItem otherLadies = clip("other-ladies");
        List<NativeContentItem> queue = Arrays.asList(ladies, chaos, otherLadies);
        assertEquals(1, ChaosFeedView.savedCategoryReturnIndex(queue, chaos.url,
                item -> item == chaos));
        assertEquals(2, ChaosFeedView.savedCategoryReturnIndex(queue, otherLadies.url,
                item -> item == otherLadies));
        assertEquals(-1, ChaosFeedView.savedCategoryReturnIndex(queue, chaos.url,
                item -> item == ladies || item == otherLadies));
        assertEquals(-1, ChaosFeedView.savedCategoryReturnIndex(queue, "missing",
                item -> true));
    }

    @Test public void emptyAndOnlyOutgoingQueuesNeverActivate() {
        NativeContentItem old = clip("old");
        assertEquals(-1, ChaosFeedView.firstReadyCategoryIndex(Collections.emptyList(), 0,
                item -> true, item -> true));
        assertEquals(-1, ChaosFeedView.firstReadyCategoryIndex(Collections.singletonList(old), 0,
                item -> true, item -> true));
    }
}
