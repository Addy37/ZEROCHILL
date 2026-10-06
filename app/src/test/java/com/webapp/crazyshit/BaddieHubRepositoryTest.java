package com.webapp.crazyshit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BaddieHubRepositoryTest {
    @Test public void acceptsOnlyBaddieHubPageHosts() {
        assertTrue(BaddieHubRepository.isBaddieHubUrl("https://baddiehub.com/lala-koi-54/"));
        assertTrue(BaddieHubRepository.isBaddieHubUrl("https://www.baddiehub.com/lala-koi-54/"));
        assertFalse(BaddieHubRepository.isBaddieHubUrl("https://cdn.baddiehub.com/a68436.mp4"));
        assertFalse(BaddieHubRepository.isBaddieHubUrl("http://baddiehub.com/lala-koi-54/"));
        assertFalse(BaddieHubRepository.isBaddieHubUrl("https://example.com/lala-koi-54/"));
    }

    @Test public void portraitGateRejectsLandscapeAndSquare() {
        assertTrue(BaddieHubRepository.isPortraitDimensions(1080, 1920, 0));
        assertTrue(BaddieHubRepository.isPortraitDimensions(720, 1280, 0));
        assertFalse(BaddieHubRepository.isPortraitDimensions(1920, 1080, 0));
        assertFalse(BaddieHubRepository.isPortraitDimensions(1080, 1080, 0));
    }

    @Test public void portraitGateHonorsRotationMetadata() {
        assertTrue(BaddieHubRepository.isPortraitDimensions(1920, 1080, 90));
        assertTrue(BaddieHubRepository.isPortraitDimensions(1920, 1080, 270));
        assertFalse(BaddieHubRepository.isPortraitDimensions(1080, 1920, 90));
    }

    @Test public void acceptsConfiguredCategoryPathsAndRejectsPaginationPaths() {
        assertEquals("only-fans", BaddieHubRepository.categorySlug(
                "https://baddiehub.com/category/only-fans/"));
        assertEquals("", BaddieHubRepository.categorySlug(
                "https://baddiehub.com/category/only-fans/page/2/"));
    }

    @Test public void showsListingKeepsAllNormalVideoCardsWithoutPortraitFiltering() {
        String html = "<main>" +
                "<a href='/portrait-clip/'><img src='/portrait.jpg' alt='Portrait clip'></a>" +
                "<a href='/landscape-clip/'><img src='/landscape.jpg' alt='Landscape clip'></a>" +
                "</main>";
        java.util.List<NativeContentItem> items = BaddieHubRepository.parseShowsListing(
                html,
                "https://baddiehub.com/category/sample/",
                "a[href]"
        );

        assertEquals(2, items.size());
        assertEquals("https://baddiehub.com/portrait-clip/", items.get(0).url);
        assertEquals("https://baddiehub.com/landscape-clip/", items.get(1).url);
    }

    @Test public void showsListingDoesNotUseShitTokEighteenItemCandidateCap() {
        StringBuilder html = new StringBuilder("<main>");
        for (int index = 0; index < 30; index++) {
            html.append("<a href='/clip-").append(index).append("/'>")
                    .append("<img src='/clip-").append(index).append(".jpg'></a>");
        }
        html.append("</main>");

        assertEquals(30, BaddieHubRepository.parseShowsListing(
                html.toString(),
                "https://baddiehub.com/category/sample/",
                "a[href]"
        ).size());
    }
}
