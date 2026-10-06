package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public final class ShitTokQueuePrunerTest {
    @Test
    public void blockingSelectedCreatorKeepsEarlierClipsAndMovesToNextClip() {
        NativeContentItem earlier = media("Earlier", "Other", "https://example.com/earlier");
        NativeContentItem selected = media(
                "Selected", "Sophie Rain", "https://fapello.com/sophie-rain/selected"
        );
        NativeContentItem sameCreator = media(
                "Later same creator", "Sophie Rain", "https://fapello.com/sophie-rain/later"
        );
        NativeContentItem next = media("Next", "Other", "https://example.com/next");
        ArrayList<NativeContentItem> queue = new ArrayList<>();
        Collections.addAll(queue, earlier, selected, sameCreator, next);

        ShitTokQueuePruner.Result result = ShitTokQueuePruner.removeBlockedCreators(
                queue,
                new HashSet<>(Collections.singleton(
                        ShitTokBlockedCreatorStore.keyForName("Sophie Rain")
                )),
                1
        );

        assertEquals(2, result.removed.size());
        assertTrue(result.selectedRemoved);
        assertEquals(1, result.targetPosition);
        assertEquals(earlier, queue.get(0));
        assertEquals(next, queue.get(1));
    }

    @Test
    public void blockingLastClipMovesToPreviousRemainingClip() {
        NativeContentItem earlier = media("Earlier", "Other", "https://example.com/earlier");
        NativeContentItem selected = media(
                "Selected", "Jane Doe", "https://fapello.com/jane-doe/selected"
        );
        ArrayList<NativeContentItem> queue = new ArrayList<>();
        Collections.addAll(queue, earlier, selected);

        ShitTokQueuePruner.Result result = ShitTokQueuePruner.removeBlockedCreators(
                queue,
                new HashSet<>(Collections.singleton(
                        ShitTokBlockedCreatorStore.keyForName("Jane Doe")
                )),
                1
        );

        assertTrue(result.selectedRemoved);
        assertEquals(0, result.targetPosition);
        assertEquals(Collections.singletonList(earlier), queue);
    }

    @Test
    public void blockingClusterChoosesActuallyNearestPreviousClip() {
        NativeContentItem previous = media("Previous", "Other", "https://example.com/previous");
        NativeContentItem selected = media(
                "Selected", "Jane Doe", "https://fapello.com/jane-doe/selected"
        );
        NativeContentItem blockedOne = media(
                "Blocked one", "Jane Doe", "https://fapello.com/jane-doe/one"
        );
        NativeContentItem blockedTwo = media(
                "Blocked two", "Jane Doe", "https://fapello.com/jane-doe/two"
        );
        NativeContentItem distantNext = media("Next", "Other", "https://example.com/next");
        ArrayList<NativeContentItem> queue = new ArrayList<>();
        Collections.addAll(queue, previous, selected, blockedOne, blockedTwo, distantNext);

        ShitTokQueuePruner.Result result = ShitTokQueuePruner.removeBlockedCreators(
                queue,
                new HashSet<>(Collections.singleton(
                        ShitTokBlockedCreatorStore.keyForName("Jane Doe")
                )),
                1
        );

        assertEquals(0, result.targetPosition);
        assertEquals(previous, queue.get(result.targetPosition));
    }

    @Test
    public void blockingOnlyCreatorLeavesEmptyQueueAtZero() {
        NativeContentItem selected = media(
                "Selected", "Jane Doe", "https://fapello.com/jane-doe/selected"
        );
        ArrayList<NativeContentItem> queue = new ArrayList<>(Collections.singletonList(selected));

        ShitTokQueuePruner.Result result = ShitTokQueuePruner.removeBlockedCreators(
                queue,
                new HashSet<>(Collections.singleton(
                        ShitTokBlockedCreatorStore.keyForName("Jane Doe")
                )),
                0
        );

        assertTrue(result.selectedRemoved);
        assertEquals(0, result.targetPosition);
        assertTrue(queue.isEmpty());
    }

    @Test
    public void unrelatedSelectionKeepsSameItemAfterEarlierCreatorRemoval() {
        NativeContentItem blocked = media(
                "Blocked", "Jane Doe", "https://fapello.com/jane-doe/blocked"
        );
        NativeContentItem selected = media("Selected", "Other", "https://example.com/selected");
        ArrayList<NativeContentItem> queue = new ArrayList<>();
        Collections.addAll(queue, blocked, selected);

        ShitTokQueuePruner.Result result = ShitTokQueuePruner.removeBlockedCreators(
                queue,
                new HashSet<>(Collections.singleton(
                        ShitTokBlockedCreatorStore.keyForName("Jane Doe")
                )),
                1
        );

        assertFalse(result.selectedRemoved);
        assertEquals(0, result.targetPosition);
        assertEquals(Collections.singletonList(selected), queue);
    }

    private static NativeContentItem media(String title, String uploader, String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title,
                url,
                "",
                "",
                uploader,
                "",
                "",
                ""
        );
    }
}
