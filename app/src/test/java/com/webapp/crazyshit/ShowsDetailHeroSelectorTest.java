package com.webapp.crazyshit;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ShowsDetailHeroSelectorTest {
    private NativeContentItem item(String url, String image) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, url, url, image, "", "", "");
    }

    @Test public void loadedPlayableUniqueThumbnailsOnlyAndFallbackThreshold() {
        NativeContentItem a = item("https://example.test/a", "https://example.test/a.jpg");
        NativeContentItem duplicate = item("https://example.test/b", a.imageUrl);
        NativeContentItem noImage = item("https://example.test/c", "");
        NativeContentItem invalid = item("", "https://example.test/d.jpg");
        NativeContentItem b = item("https://example.test/e", "https://example.test/e.jpg");
        assertEquals(1, ShowsDetailHeroSelector.candidates(
                Arrays.asList(a, duplicate, noImage, invalid), new Random(1)).size());
        List<NativeContentItem> pool = ShowsDetailHeroSelector.candidates(
                Arrays.asList(a, duplicate, noImage, invalid, b), new Random(1));
        assertEquals(2, pool.size());
        assertEquals(-1, ShowsDetailHeroSelector.next(0, 1, new Random(1)));
        assertTrue(ShowsDetailHeroSelector.candidates(Collections.emptyList(), new Random(1)).isEmpty());
    }

    @Test public void rotationNeverImmediatelyRepeats() {
        Random random = new Random(17);
        for (int size = 2; size <= 20; size++) {
            int selected = -1;
            for (int i = 0; i < 200; i++) {
                int next = ShowsDetailHeroSelector.next(selected, size, random);
                assertTrue(next >= 0 && next < size);
                assertNotEquals(selected, next);
                selected = next;
            }
        }
    }
}
