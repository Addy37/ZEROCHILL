package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ShowsHeroPicksTest {
    private static NativeContentItem cover(String provider, String name) {
        return new NativeContentItem(
                NativeContentItem.KIND_SERIES, name,
                "https://" + provider + ".example.com/series/" + name,
                "https://img.example.com/" + provider + "/" + name + ".jpg",
                "", "", "");
    }

    @Test public void heroDrawsFromAllFiveLoadedProviders() {
        List<List<NativeContentItem>> sources = Arrays.asList(
                Collections.singletonList(cover("crazyshit", "a")),
                Collections.singletonList(cover("kaotic", "b")),
                Collections.singletonList(cover("efukt", "c")),
                Collections.singletonList(cover("xmega", "d")),
                Collections.singletonList(cover("baddie", "e"))
        );
        List<NativeContentItem> result = ShowsHeroPicks.mixed(
                sources, Collections.emptySet(), 17, 8);
        assertEquals(5, result.size());
        for (int index = 0; index < 5; index++) {
            assertEquals(sources.get(index).get(0).url, result.get(index).url);
        }
    }

    @Test public void keepsTakingTurnsAndRespectsLimit() {
        List<List<NativeContentItem>> sources = Arrays.asList(
                Arrays.asList(cover("crazyshit", "a"), cover("crazyshit", "b")),
                Arrays.asList(cover("kaotic", "a"), cover("kaotic", "b")),
                Arrays.asList(cover("efukt", "a"), cover("efukt", "b"))
        );
        List<NativeContentItem> result = ShowsHeroPicks.mixed(
                sources, Collections.emptySet(), 7, 4);
        assertEquals(4, result.size());
        assertTrue(result.get(0).url.contains("crazyshit"));
        assertTrue(result.get(1).url.contains("kaotic"));
        assertTrue(result.get(2).url.contains("efukt"));
        assertTrue(result.get(3).url.contains("crazyshit"));
    }

    @Test public void filtersPlaceholdersRepeatedArtworkAndRejectedImages() {
        NativeContentItem good = cover("kaotic", "a");
        NativeContentItem duplicatePage = new NativeContentItem(
                NativeContentItem.KIND_SERIES, "duplicate", good.url + "/",
                "https://img.example.com/other.jpg", "", "", "");
        NativeContentItem duplicateImage = new NativeContentItem(
                NativeContentItem.KIND_SERIES, "second", "https://other.example.com/series",
                good.imageUrl, "", "", "");
        NativeContentItem placeholder = new NativeContentItem(
                NativeContentItem.KIND_SERIES, "placeholder", "https://other.example.com/blank",
                "https://img.example.com/no_image.jpg", "", "", "");
        NativeContentItem rejected = cover("efukt", "bad");
        List<NativeContentItem> picks = ShowsHeroPicks.mixed(Arrays.asList(
                Arrays.asList(good, duplicatePage, duplicateImage, placeholder),
                Collections.singletonList(rejected)
        ), new HashSet<>(Collections.singletonList(ShowsHeroPicks.imageKey(rejected))), 3, 8);
        assertEquals(1, picks.size());
        assertEquals(good.url, picks.get(0).url);
        assertFalse(ShowsHeroPicks.hasRemoteArtwork(placeholder));
    }

    @Test public void rejectsTinyAndBadlyShapedImagesButKeepsRealCovers() {
        assertTrue(ShowsHeroPicks.usableArtworkSize(1280, 720));
        assertTrue(ShowsHeroPicks.usableArtworkSize(600, 900));
        assertFalse(ShowsHeroPicks.usableArtworkSize(120, 80));
        assertFalse(ShowsHeroPicks.usableArtworkSize(64, 64));
        assertFalse(ShowsHeroPicks.usableArtworkSize(2048, 200));
        assertFalse(ShowsHeroPicks.usableArtworkSize(0, 500));
    }

    @Test public void isStableAcrossRepeatedProviderRefreshesWithSameSeed() {
        List<List<NativeContentItem>> sources = Arrays.asList(
                Arrays.asList(cover("crazyshit", "one"), cover("crazyshit", "two"),
                        cover("crazyshit", "three")),
                Arrays.asList(cover("kaotic", "one"), cover("kaotic", "two"))
        );
        List<NativeContentItem> first = ShowsHeroPicks.mixed(
                sources, Collections.emptySet(), 123, 8);
        List<NativeContentItem> second = ShowsHeroPicks.mixed(
                sources, Collections.emptySet(), 123, 8);
        assertEquals(first.size(), second.size());
        for (int index = 0; index < first.size(); index++) {
            assertEquals(first.get(index).url, second.get(index).url);
        }
    }
}
