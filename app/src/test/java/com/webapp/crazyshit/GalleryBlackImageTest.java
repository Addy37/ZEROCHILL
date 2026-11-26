package com.webapp.crazyshit;

import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class GalleryBlackImageTest {
    @Test public void onlyExactOpaqueBlackIsOmitted() {
        assertTrue(GalleryBlackImage.allOpaqueBlack(new int[]{Color.BLACK, Color.BLACK}));
        assertFalse(GalleryBlackImage.allOpaqueBlack(new int[]{Color.BLACK, Color.rgb(1, 1, 1)}));
        assertFalse(GalleryBlackImage.allOpaqueBlack(new int[]{Color.BLACK, Color.TRANSPARENT}));
        assertFalse(GalleryBlackImage.allOpaqueBlack(new int[]{Color.BLACK, Color.WHITE}));
        assertFalse(GalleryBlackImage.allOpaqueBlack(new int[0]));
    }
    private NativeContentItem image(String kind, String extension) {
        return new NativeContentItem(kind, "Example", "https://media.cum.st/example." + extension,
                "https://img.cum.st/example.jpg", "", "https://cum.st/creators/test/example", "");
    }
    @Test public void failureOfOriginalRetriesOnceThenOmitsOnlyStaticPhotos() {
        NativeContentItem photo = image(NativeContentItem.KIND_IMAGE, "jpg");
        NativeContentItem webp = image(NativeContentItem.KIND_IMAGE, "webp");
        NativeContentItem avif = image(NativeContentItem.KIND_IMAGE, "avif");
        NativeContentItem gif = image(NativeContentItem.KIND_IMAGE, "gif");
        NativeContentItem video = image(NativeContentItem.KIND_MEDIA, "mp4");
        assertEquals(GalleryBlackImage.FailureAction.KEEP,
                GalleryBlackImage.originalFailureAction(photo, photo.imageUrl, false));
        assertEquals(GalleryBlackImage.FailureAction.RETRY,
                GalleryBlackImage.originalFailureAction(photo, photo.url, false));
        assertEquals(GalleryBlackImage.FailureAction.OMIT,
                GalleryBlackImage.originalFailureAction(photo, photo.url, true));
        assertTrue(GalleryBlackImage.eligible(webp));
        assertTrue(GalleryBlackImage.eligible(avif));
        assertEquals(GalleryBlackImage.FailureAction.KEEP,
                GalleryBlackImage.originalFailureAction(gif, gif.url, true));
        assertEquals(GalleryBlackImage.FailureAction.KEEP,
                GalleryBlackImage.originalFailureAction(video, video.url, true));
    }

    @Test public void staticPhotosOnlyAndOmissionResetsOnRefresh() {
        NativeContentItem black = image(NativeContentItem.KIND_IMAGE, "jpg");
        NativeContentItem gif = image(NativeContentItem.KIND_IMAGE, "gif");
        NativeContentItem video = image(NativeContentItem.KIND_MEDIA, "mp4");
        assertTrue(GalleryBlackImage.eligible(black));
        assertFalse(GalleryBlackImage.eligible(gif));
        assertFalse(GalleryBlackImage.eligible(video));
        BunkrGalleryAdapter adapter = new BunkrGalleryAdapter(
                org.robolectric.RuntimeEnvironment.getApplication(), new BunkrGalleryAdapter.Listener() {
                    public void onOpen(int p, NativeContentItem i, android.view.View v) {}
                    public void onLongPress(NativeContentItem i, android.view.View v) {}
                });
        java.util.List<NativeContentItem> items = java.util.Arrays.asList(black, gif, video);
        adapter.replace(items, false);
        adapter.omitBlackImage(black.url);
        assertEquals(2, adapter.size());
        adapter.append(items, false);
        assertEquals(2, adapter.size());
        adapter.resetBlackImageOmissions();
        adapter.replace(items, false);
        assertEquals(3, adapter.size());
    }
}
