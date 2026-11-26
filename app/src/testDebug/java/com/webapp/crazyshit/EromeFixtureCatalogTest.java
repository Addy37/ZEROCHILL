package com.webapp.crazyshit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class EromeFixtureCatalogTest {
    @Test public void pagesAreBoundedUniqueAndTerminate() {
        Set<Integer> seen = new HashSet<>();
        for (int offset = 0; offset < 9; offset += 3) {
            List<EromeFixtureCatalog.Album> page = EromeFixtureCatalog.page("", "", offset);
            assertEquals(3, page.size());
            for (EromeFixtureCatalog.Album album : page) assertTrue(seen.add(album.id));
        }
        assertTrue(EromeFixtureCatalog.page("", "", 9).isEmpty());
        assertEquals(9, seen.size());
    }
    @Test public void uploaderAndTagFiltersRemainExact() {
        assertEquals(4, EromeFixtureCatalog.matching("Studio North", "").size());
        for (EromeFixtureCatalog.Album a : EromeFixtureCatalog.matching("Studio North", "design")) {
            assertEquals("Studio North", a.uploader);
            assertTrue(a.tags.contains("design"));
        }
        assertTrue(EromeFixtureCatalog.matching("", "no-fixture-match").isEmpty());
    }
    @Test public void galleriesHaveUniqueInertIdsAndCorrectMediaCounts() {
        for (EromeFixtureCatalog.Album a : EromeFixtureCatalog.matching("", "")) {
            assertEquals(a.pictures, a.media("pictures").size());
            assertEquals(a.videos, a.media("videos").size());
            assertEquals(a.pictures + a.videos, a.media("all").size());
            Set<String> seen = new HashSet<>();
            for (NativeContentItem item : a.media("all")) {
                assertTrue(seen.add(item.url));
                assertTrue(item.url.startsWith(EromeFixtureCatalog.ORIGIN));
                assertTrue(EromeFixtureCatalog.isArtwork(item.imageUrl));
            }
            for (NativeContentItem item : a.media("pictures")) assertTrue(item.isImage());
            for (NativeContentItem item : a.media("videos")) assertTrue(item.isVideo());
        }
        assertFalse(EromeFixtureCatalog.isArtwork("https://example.com/art/1"));
    }
}
