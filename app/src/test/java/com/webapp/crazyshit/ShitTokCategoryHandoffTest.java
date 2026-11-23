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

    @Test public void emptyAndOnlyOutgoingQueuesNeverActivate() {
        NativeContentItem old = clip("old");
        assertEquals(-1, ChaosFeedView.firstReadyCategoryIndex(Collections.emptyList(), 0,
                item -> true, item -> true));
        assertEquals(-1, ChaosFeedView.firstReadyCategoryIndex(Collections.singletonList(old), 0,
                item -> true, item -> true));
    }
}
