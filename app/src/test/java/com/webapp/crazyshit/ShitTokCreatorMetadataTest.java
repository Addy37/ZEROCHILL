package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ShitTokCreatorMetadataTest {
    @Test
    public void fapelloClip_usesUploaderAsCreatorName() {
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "493857_video_final",
                "https://fapello.com/video/12345/",
                "",
                "",
                "Sophie Rain",
                "",
                "OnlyFap"
        );

        assertEquals("Sophie Rain", ShitTokCreatorMetadata.creatorName(item));
        assertTrue(ShitTokCreatorMetadata.hasCreator(item));
    }

    @Test
    public void onlyHavenClip_usesTitleAsCreatorName() {
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Jane Doe",
                "https://img.cum.st/post/video.mp4",
                "",
                "OnlyHaven",
                "OnlyHaven",
                "",
                "onlyfans · OnlyHaven"
        );

        assertEquals("Jane Doe", ShitTokCreatorMetadata.creatorName(item));
        assertTrue(ShitTokCreatorMetadata.hasCreator(item));
    }

    @Test
    public void creatorIdentity_usesCreatorNameAndClipArtworkAsFallback() {
        NativeContentItem media = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Jane Doe",
                "https://img.cum.st/post/video.mp4",
                "https://img.cum.st/post/preview.webp",
                "OnlyHaven",
                "OnlyHaven",
                "",
                "onlyfans · OnlyHaven"
        );

        NativeContentItem creator = ShitTokCreatorMetadata.creatorIdentity(null, media);

        assertEquals(NativeContentItem.KIND_CREATOR, creator.kind);
        assertEquals("Jane Doe", creator.title);
        assertEquals("Jane Doe", creator.searchQuery);
        assertEquals("https://img.cum.st/post/preview.webp", creator.imageUrl);
    }

    @Test
    public void creatorIdentity_regularShitTokClipHasNoCreator() {
        NativeContentItem media = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Normal video title",
                "https://crazyshit.com/cnt/medias/123",
                "https://crazyshit.com/thumb.jpg",
                "",
                "Uploader",
                "",
                ""
        );

        assertEquals(null, ShitTokCreatorMetadata.creatorIdentity(null, media));
    }

    @Test
    public void creatorGallerySwipe_requiresStrongLeftHorizontalGesture() {
        assertTrue(ChaosFeedView.shouldOpenCreatorGallerySwipe(-120f, 20f, 72f));
        assertFalse(ChaosFeedView.shouldOpenCreatorGallerySwipe(-55f, 5f, 72f));
        assertFalse(ChaosFeedView.shouldOpenCreatorGallerySwipe(-120f, 115f, 72f));
        assertFalse(ChaosFeedView.shouldOpenCreatorGallerySwipe(120f, 5f, 72f));
    }

    @Test
    public void creatorGallerySwipe_tracksFingerAcrossFullWidth() {
        assertEquals(-120f, ChaosFeedView.creatorSwipeContentTranslation(-120f, 1080f), 0.001f);
        assertEquals(960f, ChaosFeedView.creatorSwipePreviewTranslation(-120f, 1080f), 0.001f);
        assertEquals(-1080f, ChaosFeedView.creatorSwipeContentTranslation(-1400f, 1080f), 0.001f);
        assertEquals(0f, ChaosFeedView.creatorSwipePreviewTranslation(-1400f, 1080f), 0.001f);
        assertEquals(0f, ChaosFeedView.creatorSwipeContentTranslation(80f, 1080f), 0.001f);
        assertEquals(1080f, ChaosFeedView.creatorSwipePreviewTranslation(80f, 1080f), 0.001f);
    }

    @Test
    public void creatorGalleryReturn_tracksRightDragAndRequiresHorizontalIntent() {
        assertEquals(180f, NativeFeedBrowserActivity.shitTokReturnTranslation(180f, 1080f), 0.001f);
        assertEquals(0f, NativeFeedBrowserActivity.shitTokReturnTranslation(-40f, 1080f), 0.001f);
        assertEquals(1080f, NativeFeedBrowserActivity.shitTokReturnTranslation(1400f, 1080f), 0.001f);
        assertTrue(NativeFeedBrowserActivity.shouldCommitShitTokReturn(220f, 30f, 180f));
        assertFalse(NativeFeedBrowserActivity.shouldCommitShitTokReturn(120f, 20f, 180f));
        assertFalse(NativeFeedBrowserActivity.shouldCommitShitTokReturn(220f, 210f, 180f));
    }

    @Test
    public void regularShitTokClip_doesNotBecomeCreatorLink() {
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Normal video title",
                "https://crazyshit.com/cnt/medias/123",
                "",
                "",
                "Uploader",
                "",
                ""
        );

        assertEquals("", ShitTokCreatorMetadata.creatorName(item));
        assertFalse(ShitTokCreatorMetadata.hasCreator(item));
    }
}
