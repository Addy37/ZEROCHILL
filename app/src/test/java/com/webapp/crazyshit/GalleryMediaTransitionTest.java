package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class GalleryMediaTransitionTest {
    @Test
    public void transitionNameIsStableForTheTappedMedia() {
        NativeContentItem first = item(
                NativeContentItem.KIND_IMAGE,
                "https://fapello.com/creator/post-1/"
        );
        NativeContentItem same = item(
                NativeContentItem.KIND_IMAGE,
                "https://fapello.com/creator/post-1/"
        );
        NativeContentItem second = item(
                NativeContentItem.KIND_IMAGE,
                "https://fapello.com/creator/post-2/"
        );

        assertTrue(GalleryMediaTransition.transitionName(first)
                .startsWith("zerochill_gallery_media_"));
        assertTrue(GalleryMediaTransition.transitionName(first)
                .equals(GalleryMediaTransition.transitionName(same)));
        assertNotEquals(
                GalleryMediaTransition.transitionName(first),
                GalleryMediaTransition.transitionName(second)
        );
    }

    @Test
    public void sharedReturnOnlyTargetsTheOriginallyOpenedMedia() {
        NativeContentItem first = item(
                NativeContentItem.KIND_IMAGE,
                "https://fapello.com/creator/post-1/"
        );
        NativeContentItem second = item(
                NativeContentItem.KIND_IMAGE,
                "https://fapello.com/creator/post-2/"
        );

        assertTrue(BunkrGalleryActivity.canReturnWithSharedElement(
                "zerochill_gallery_media_1234",
                first.url,
                first
        ));
        assertFalse(BunkrGalleryActivity.canReturnWithSharedElement(
                "zerochill_gallery_media_1234",
                first.url,
                second
        ));
        assertFalse(BunkrGalleryActivity.canReturnWithSharedElement(
                "",
                first.url,
                first
        ));
    }

    @Test
    public void tappedVideoWaitsForExpansionBeforeAutoplay() {
        NativeContentItem video = item(
                NativeContentItem.KIND_VIDEO,
                "https://fapello.com/creator/video-1/"
        );

        assertTrue(BunkrGalleryActivity.shouldDelayInitialAutoplay(
                true,
                true,
                video.url,
                video
        ));
        assertFalse(BunkrGalleryActivity.shouldDelayInitialAutoplay(
                false,
                true,
                video.url,
                video
        ));
        assertFalse(BunkrGalleryActivity.shouldDelayInitialAutoplay(
                true,
                false,
                video.url,
                video
        ));
    }

    private static NativeContentItem item(String kind, String url) {
        return new NativeContentItem(
                kind,
                "Media",
                url,
                "https://cdn.example.com/thumb.jpg",
                "",
                "",
                "",
                ""
        );
    }
}
