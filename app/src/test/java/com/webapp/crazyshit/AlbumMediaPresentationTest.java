package com.webapp.crazyshit;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class AlbumMediaPresentationTest {
    @Test public void picturesAreSeparateFromVideoRows() {
        NativeContentItem picture = new NativeContentItem(NativeContentItem.KIND_IMAGE,
                "Photo", "https://example.invalid/photo.jpg", "", "", "", "");
        NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "Video", "https://example.invalid/video.mp4", "", "", "", "");
        assertEquals(Arrays.asList(picture),
                AlbumMediaPresentation.pictures(Arrays.asList(video, picture)));
    }

    @Test public void fullWidthHeaderVideosAndFooterOnlyPicturesUseGridCells() {
        for (int i = 0; i < 6; i++) {
            assertEquals(i > 0 && i <= 3 ? 1 : 2,
                    AlbumMediaPresentation.spanSize(i, 3, 2));
            assertEquals(2, AlbumMediaPresentation.spanSize(i, 0, 2));
        }
    }

    @Test public void singleMediaTypeNeverKeepsAnEmptyFilter() {
        assertEquals(AlbumMediaPresentation.ALL,
                AlbumMediaPresentation.availableFilter(AlbumMediaPresentation.VIDEOS, 3, 0));
        assertEquals(AlbumMediaPresentation.ALL,
                AlbumMediaPresentation.availableFilter(AlbumMediaPresentation.PICTURES, 0, 3));
        assertEquals(AlbumMediaPresentation.PICTURES,
                AlbumMediaPresentation.availableFilter(AlbumMediaPresentation.PICTURES, 3, 2));
        assertEquals(AlbumMediaPresentation.ALL, AlbumMediaPresentation.normalize(99));
    }
}
