package com.webapp.crazyshit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class GalleryThumbnailPipelineTest {
    @Test public void repeatedTransientFailuresPauseOnlyThumbnailAttempts() {
        GalleryThumbnailPipeline.Cooldown cooldown = new GalleryThumbnailPipeline.Cooldown();
        cooldown.failure(502, 10);
        cooldown.failure(502, 20);
        assertFalse(cooldown.skip(21));
        cooldown.failure(503, 30);
        assertTrue(cooldown.skip(31));
        assertFalse(cooldown.skip(30_030));
        cooldown.success();
        assertFalse(cooldown.skip(31));
    }

    @Test public void tailSkeletonsAreBoundedAndNeverCountAsMedia() {
        assertEquals(0, BunkrGalleryAdapter.skeletonCount(0, 2));
        assertEquals(1, BunkrGalleryAdapter.skeletonCount(1, 2));
        assertEquals(4, BunkrGalleryAdapter.skeletonCount(50, 2));
        assertEquals(10, BunkrGalleryAdapter.skeletonCount(50, 5));
    }

    @Test public void supportsOnlyAffectedProviderStaticPhotosAndVideos() {
        NativeContentItem photo = new NativeContentItem(NativeContentItem.KIND_IMAGE, "photo",
                "https://media.cum.st/test.jpg", "", "", "https://cum.st/creators/test/example", "");
        NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA, "video",
                "https://media.cum.st/test.mp4", "", "", photo.uploader, "");
        NativeContentItem gif = new NativeContentItem(NativeContentItem.KIND_IMAGE, "gif",
                "https://media.cum.st/test.gif", "", "", photo.uploader, "");
        NativeContentItem other = new NativeContentItem(NativeContentItem.KIND_IMAGE, "photo",
                "https://example.invalid/test.jpg", "", "", "https://example.invalid/profile", "");
        assertTrue(GalleryThumbnailPipeline.supports(photo));
        assertTrue(GalleryThumbnailPipeline.supports(video));
        assertFalse(GalleryThumbnailPipeline.supports(gif));
        assertFalse(GalleryThumbnailPipeline.supports(other));
    }
}
