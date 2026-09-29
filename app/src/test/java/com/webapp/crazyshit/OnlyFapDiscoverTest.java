package com.webapp.crazyshit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OnlyFapDiscoverTest {
    @Test
    public void discoverUsesDeeperFapelloPagesAndKeepsLargeCandidatePool() {
        assertEquals(2, FapzoneCreatorRepository.DISCOVER_START_PAGE);
        assertEquals(3, FapzoneCreatorRepository.DISCOVER_END_PAGE);
        assertEquals(16, FapzoneCreatorRepository.DISCOVER_ITEMS);
        assertEquals(48, FapzoneCreatorRepository.DISCOVER_CANDIDATES);
    }

    @Test
    public void discoverExcludesCreatorsAlreadyVisibleInOtherShelves() {
        ArrayList<NativeContentItem> candidates = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            candidates.add(creator("Creator " + i, "https://fapello.com/creator-" + i + "/"));
        }

        List<NativeContentItem> newItems = Collections.singletonList(
                creator("Creator 2", "https://fapello.com/creator-2/")
        );
        List<NativeContentItem> hotItems = Collections.singletonList(
                creator("Creator 4", "https://fapello.com/different-url/")
        );
        List<NativeContentItem> popularItems = Collections.singletonList(
                creator("Creator 6", "https://fapello.com/creator-6/")
        );

        ArrayList<NativeContentItem> selected =
                FapzoneCreatorRepository.selectDiscoverItems(
                        candidates,
                        newItems,
                        hotItems,
                        popularItems
                );

        assertEquals(16, selected.size());
        assertFalse(containsTitle(selected, "Creator 2"));
        assertFalse(containsTitle(selected, "Creator 4"));
        assertFalse(containsTitle(selected, "Creator 6"));
        assertTrue(containsTitle(selected, "Creator 1"));
    }

    @Test
    public void discoverDeduplicatesCandidatesBeforeFillingShelf() {
        ArrayList<NativeContentItem> candidates = new ArrayList<>();
        candidates.add(creator("Same Creator", "https://fapello.com/same-creator/"));
        candidates.add(creator("Same Creator", "https://fapello.com/same-creator/"));
        for (int i = 1; i <= 20; i++) {
            candidates.add(creator("Other " + i, "https://fapello.com/other-" + i + "/"));
        }

        ArrayList<NativeContentItem> selected =
                FapzoneCreatorRepository.selectDiscoverItems(
                        candidates,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                );

        assertEquals(16, selected.size());
        int duplicateCount = 0;
        for (NativeContentItem item : selected) {
            if ("Same Creator".equals(item.title)) duplicateCount++;
        }
        assertEquals(1, duplicateCount);
    }

    private static NativeContentItem creator(String title, String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                title,
                url,
                "https://fapello.com/content/" + title.replace(" ", "-") + ".jpg",
                "",
                url,
                "",
                "Discover test",
                title
        );
    }

    private static boolean containsTitle(List<NativeContentItem> items, String title) {
        for (NativeContentItem item : items) {
            if (item != null && title.equals(item.title)) return true;
        }
        return false;
    }
}
