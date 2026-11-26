package com.webapp.crazyshit;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BaddieHubSearchFallbackTest {
    @Test public void emptySearchKeepsRandomVideosBehindAnExplicitMarker() {
        String html = "<h1 class='widget-title'>Nothing found</h1>"
                + "<div class='notfound-videos'><h2>Random videos</h2>"
                + "<a href='https://baddiehub.com/random-clip/'>"
                + "<img src='https://baddiehub.com/thumb.jpg' alt='Random Clip'></a>"
                + "</div>";
        List<NativeContentItem> items = BaddieHubRepository.parseSearchListing(
                html, "https://baddiehub.com/?s=zzzz", "a[href]");
        assertTrue(BaddieHubRepository.isRandomFallbackMarker(items.get(0)));
        assertEquals("Random Clip", items.get(1).title);
        assertEquals("https://baddiehub.com/random-clip/", items.get(1).url);
        assertFalse(items.get(1).isSection());
    }

    @Test public void realSearchResultsStayUnmarked() {
        String html = "<div class='page-header'><a href='/?s=aaliyah&filter=random'>Random videos</a></div>"
                + "<a href='https://baddiehub.com/aaliyah-h-4/'>"
                + "<img src='https://baddiehub.com/a.jpg' alt='Aaliyah H'></a>";
        List<NativeContentItem> items = BaddieHubRepository.parseSearchListing(
                html, "https://baddiehub.com/?s=aaliyah", "a[href]");
        assertEquals(1, items.size());
        assertFalse(BaddieHubRepository.isRandomFallbackMarker(items.get(0)));
        assertEquals("Aaliyah H", items.get(0).title);
    }
}
