package com.webapp.crazyshit;

import org.junit.Test;

import java.io.IOException;
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

    @Test
    public void refillStartsOutsideEveryRegularFeedsLiveRange() throws Exception {
        assertEquals(4, FapzoneCreatorRepository.LIVE_PAGES);
        assertTrue(FapzoneCreatorRepository.DISCOVER_REFILL_START_PAGE
                > FapzoneCreatorRepository.LIVE_PAGES);
        assertEquals("https://fapello.com/page-5/",
                FapelloRepository.listingUrl(FapelloRepository.LIST_NEW,
                        FapzoneCreatorRepository.DISCOVER_REFILL_START_PAGE));
        assertEquals("https://fapello.com/hot-5/",
                FapelloRepository.listingUrl(FapelloRepository.LIST_HOT, 5));
        assertEquals("https://fapello.com/popular-5/",
                FapelloRepository.listingUrl(FapelloRepository.LIST_POPULAR, 5));
    }

    @Test
    public void overlappingPoolRefillsInsteadOfSettlingAtTwoCreators() {
        List<NativeContentItem> initial = creators(0, 48);
        List<NativeContentItem> regular = creators(0, 46);
        List<Integer> pages = new ArrayList<>();
        List<NativeContentItem> pool = FapzoneCreatorRepository.refillDiscoverCandidates(
                initial, Collections.emptyList(), regular,
                Collections.emptyList(), Collections.emptyList(), (listing, page) -> {
                    pages.add(page);
                    // A thin first deeper page requires a second page round.
                    if (!FapelloRepository.LIST_NEW.equals(listing)) return Collections.emptyList();
                    return models(page == 5 ? 100 : 200, page == 5 ? 2 : 30);
                });
        List<NativeContentItem> selected = FapzoneCreatorRepository.selectDiscoverItems(
                pool, regular, Collections.emptyList(), Collections.emptyList());
        assertEquals(16, selected.size());
        assertTrue(pages.contains(5));
        assertTrue(pages.contains(6));
        for (NativeContentItem item : regular) assertFalse(containsTitle(selected, item.title));
    }

    @Test
    public void fullUniquePoolDoesNotRequestUnnecessaryRefillPages() {
        List<NativeContentItem> initial = creators(100, 48);
        List<NativeContentItem> pool = FapzoneCreatorRepository.refillDiscoverCandidates(
                initial, Collections.emptyList(), creators(0, 30), creators(30, 30),
                creators(60, 30), (listing, page) -> {
                    throw new AssertionError("Full Discover must not fetch refill pages");
                });
        assertEquals(48, pool.size());
        assertEquals(16, FapzoneCreatorRepository.selectDiscoverItems(pool,
                creators(0, 30), creators(30, 30), creators(60, 30)).size());
    }

    @Test
    public void aliasNamesAndCanonicalUrlsCannotDuplicateInsideDiscover() {
        List<NativeContentItem> candidates = creators(100, 20);
        candidates.add(0, creator("Same Name", "https://fapello.com/first/"));
        candidates.add(1, creator("same_name", "https://fapello.com/alias/"));
        candidates.add(2, creator("Different Name", "https://FAPELLO.com/first"));
        List<NativeContentItem> selected = FapzoneCreatorRepository.selectDiscoverItems(
                candidates, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertEquals(16, selected.size());
        assertFalse(containsTitle(selected, "same_name"));
        assertFalse(containsTitle(selected, "Different Name"));
    }

    @Test
    public void failedPagesRetainCacheAndPartialFreshResultsWithoutDuplicates() {
        List<NativeContentItem> cached = creators(100, 30);
        List<NativeContentItem> pool = FapzoneCreatorRepository.refillDiscoverCandidates(
                creators(100, 2), cached, creators(0, 30), Collections.emptyList(),
                Collections.emptyList(), (listing, page) -> { throw new IOException("offline"); });
        assertEquals(30, pool.size());
        assertEquals(16, FapzoneCreatorRepository.selectDiscoverItems(pool,
                creators(0, 30), Collections.emptyList(), Collections.emptyList()).size());
    }

    @Test
    public void brokenListingDoesNotBlockOtherListingsAndInvalidArtworkIsIgnored() {
        List<NativeContentItem> pool = FapzoneCreatorRepository.refillDiscoverCandidates(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), (listing, page) -> {
                    if (FapelloRepository.LIST_NEW.equals(listing)) throw new IOException("unavailable");
                    List<FapelloRepository.Model> models = models(200, 30);
                    models.add(0, new FapelloRepository.Model("Bad", "https://fapello.com/bad/", ""));
                    return models;
                });
        assertEquals(30, pool.size());
        assertFalse(containsTitle(pool, "Bad"));
    }

    @Test
    public void sourceExhaustionReturnsUsefulPartialResultsWithoutRelaxingExclusions() {
        List<NativeContentItem> regular = creators(0, 46);
        List<NativeContentItem> pool = FapzoneCreatorRepository.refillDiscoverCandidates(
                creators(0, 48), Collections.emptyList(), regular, Collections.emptyList(),
                Collections.emptyList(), (listing, page) -> Collections.emptyList());
        assertEquals(2, pool.size());
        assertFalse(containsTitle(pool, "Creator 0"));
    }

    static List<NativeContentItem> creators(int first, int count) {
        ArrayList<NativeContentItem> items = new ArrayList<>();
        for (int i = first; i < first + count; i++) {
            items.add(creator("Creator " + i, "https://fapello.com/creator-" + i + "/"));
        }
        return items;
    }

    private static List<FapelloRepository.Model> models(int first, int count) {
        ArrayList<FapelloRepository.Model> models = new ArrayList<>();
        for (NativeContentItem item : creators(first, count)) {
            models.add(new FapelloRepository.Model(item.title, item.url, item.imageUrl));
        }
        return models;
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
