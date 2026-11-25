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

    @Test public void combinedGridPreservesOrderAndFiltersMedia() {
        NativeContentItem picture = new NativeContentItem(NativeContentItem.KIND_IMAGE,
                "Photo", "https://example.invalid/photo.jpg", "", "", "", "");
        NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "Video", "https://example.invalid/video.mp4", "", "", "", "");
        assertEquals(Arrays.asList(video, picture),
                AlbumMediaPresentation.visible(Arrays.asList(video, picture), AlbumMediaPresentation.ALL));
        assertEquals(Arrays.asList(video),
                AlbumMediaPresentation.visible(Arrays.asList(video, picture), AlbumMediaPresentation.VIDEOS));
        assertEquals(Arrays.asList(picture),
                AlbumMediaPresentation.visible(Arrays.asList(video, picture), AlbumMediaPresentation.PICTURES));
        assertEquals("1 picture · 1 video", AlbumMediaPresentation.countLabel(1, 1));
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
