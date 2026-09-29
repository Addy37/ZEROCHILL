package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OnlyFapSpeedPolicyTest {
    @Test public void nearestGalleryVideosGetMorePreloadBytes() {
        assertEquals(1024L * 1024L, GalleryVideoCache.preloadBytesForDistance(1));
        assertEquals(512L * 1024L, GalleryVideoCache.preloadBytesForDistance(2));
        assertEquals(256L * 1024L, GalleryVideoCache.preloadBytesForDistance(3));
        assertEquals(256L * 1024L, GalleryVideoCache.preloadBytesForDistance(5));
    }

    @Test public void tappedGalleryVideoAutoplaysOnlyForMatchingFreshSelection() {
        String url = "https://example.test/video/123";
        NativeContentItem video = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Video",
                url,
                "",
                "",
                "",
                ""
        );
        NativeContentItem image = new NativeContentItem(
                NativeContentItem.KIND_IMAGE,
                "Photo",
                url,
                "",
                "",
                "",
                ""
        );

        assertTrue(BunkrGalleryActivity.shouldAutoplayInitialSelection(true, url, video));
        assertFalse(BunkrGalleryActivity.shouldAutoplayInitialSelection(false, url, video));
        assertFalse(BunkrGalleryActivity.shouldAutoplayInitialSelection(
                true,
                "https://example.test/video/other",
                video
        ));
        assertFalse(BunkrGalleryActivity.shouldAutoplayInitialSelection(true, url, image));
    }
}
