package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class EromeRepositoryTest {
    private static final String SEARCH = "<html><head>"
            + "<link rel=\"next\" href=\"https://www.erome.com/search?q=nature&page=2\" />"
            + "</head><body><form id=\"searchForm\">"
            + "<input name=\"q\" value=\"nature\"></form><div id=\"albums\">"
            + "<div class=\"album\" id=\"album-1\">"
            + "<a class=\"album-link\" href=\"https://www.erome.com/a/Abcd1234\">"
            + "<img class=\"album-thumbnail\" alt=\"Coastal landscape\" "
            + "src=\"https://s1.erome.com/1/Abcd1234/thumbs/pic.jpg\">"
            + "<span class=\"album-images\"><svg></svg>3</span>"
            + "<span class=\"album-videos\"><svg></svg>1</span>"
            + "<span class=\"album-bottom-views\"><svg></svg>11,7K</span></a>"
            + "<a class=\"album-title\" href=\"https://www.erome.com/a/Abcd1234\">Coastal landscape</a>"
            + "<span class=\"album-user\">SampleStudio</span></div>"
            + "<div class=\"album\"><a class=\"album-link\" href=\"https://www.erome.com/a/Abcd1234\">"
            + "<img class=\"album-thumbnail\" src=\"https://s1.erome.com/1/Abcd1234/thumbs/pic.jpg\">"
            + "</a><a class=\"album-title\">Duplicate</a></div>"
            + "<div class=\"album\"><a class=\"album-link\" href=\"https://evil.example/a/Abcd1234\">"
            + "<img class=\"album-thumbnail\" src=\"https://evil.example/a.jpg\"></a>"
            + "<a class=\"album-title\">Offsite</a></div>"
            + "</div></body></html>";

    private static final String ALBUM = "<html><body>"
            + "<h1 class=\"album-title-page\">Coastal landscape</h1>"
            + "<a id=\"user_name\" href=\"https://www.erome.com/SampleStudio\">SampleStudio</a>"
            + "<div class=\"media-group\"><div class=\"video\">"
            + "<video class=\"player video-js vjs-portrait\" poster=\"https://s1.erome.com/1/Abcd1234/clip.jpg\">"
            + "<source src=\"https://v1.erome.com/1/Abcd1234/clip_480p.mp4\" type=\"video/mp4\" res=\"480\">"
            + "<source src=\"https://cdn.evil.example/clip.mp4\" type=\"video/mp4\" res=\"2160\">"
            + "<source src=\"https://v1.erome.com/1/Abcd1234/clip_720p.mp4\" type=\"video/mp4\" res=\"720\">"
            + "</video><span class=\"duration\">01:02</span></div></div>"
            + "<div class=\"media-group\"><div class=\"img\">"
            + "<img class=\"img-front\" width=\"800\" height=\"1000\" "
            + "src=\"https://s1.erome.com/1/Abcd1234/still.jpg\">"
            + "<img class=\"img-back\" src=\"https://s1.erome.com/1/Abcd1234/still.jpg\">"
            + "</div></div></body></html>";

    @Test public void searchParsesAlbumsAndIgnoresDuplicatesAndOffsiteCards() throws Exception {
        EromePages.SearchPage page = EromePages.parseSearch(SEARCH);
        assertEquals(1, page.items.size());
        assertTrue(page.hasNext);
        NativeContentItem album = page.items.get(0);
        assertEquals(NativeContentItem.KIND_SERIES, album.kind);
        assertEquals("Coastal landscape", album.title);
        assertEquals("https://www.erome.com/a/Abcd1234", album.url);
        assertEquals("https://s1.erome.com/1/Abcd1234/thumbs/pic.jpg", album.imageUrl);
        assertEquals("11.7K", album.views);
        assertEquals("SampleStudio", album.uploader);
        assertEquals("3 pictures · 1 video", album.description);
        assertEquals("https://www.erome.com/a/Abcd1234", EromeRepository.imageReferer(album));
    }

    @Test public void emptySearchIsDistinctFromFailureAndVerification() throws Exception {
        String empty = "<html><body><input name=\"q\" value=\"zzznomatchtokenxyz\">No results</body></html>";
        String blocked = "<html><head><title>Just a moment...</title></head><body>cf-challenge</body></html>";
        String broken = "<html><body><div>Something else</div></body></html>";
        assertTrue(EromePages.parseSearch(empty).items.isEmpty());
        assertThrows(EromePages.AccessException.class, () -> EromePages.parseSearch(blocked));
        assertThrows(java.io.IOException.class, () -> EromePages.parseSearch(broken));
        assertFalse(new EromeRepository().search(null, "a", 1).hasNext);
        assertTrue(new EromeRepository().search(null, "a", 1).items.isEmpty());
    }

    @Test public void albumKeepsPhotosVideosAndTheHighestProviderVideo() throws Exception {
        EromePages.AlbumPage page = EromePages.parseAlbum(ALBUM, "https://www.erome.com/a/Abcd1234");
        assertEquals(2, page.items.size());
        NativeContentItem video = page.items.get(0);
        NativeContentItem photo = page.items.get(1);
        assertTrue(video.isVideo());
        assertEquals("https://www.erome.com/a/Abcd1234#clip", video.url);
        assertEquals("https://v1.erome.com/1/Abcd1234/clip_720p.mp4", video.sourceMediaUrl);
        assertEquals("01:02", video.description);
        assertEquals("SampleStudio", video.uploader);
        assertTrue(photo.isImage());
        assertEquals("https://www.erome.com/a/Abcd1234#img-still", photo.url);
        assertEquals("https://s1.erome.com/1/Abcd1234/still.jpg", photo.imageUrl);
        assertEquals(0.8f, photo.aspectRatioHint, 0.001f);
        assertEquals(9f / 16f, video.aspectRatioHint, 0.001f);
        assertEquals("https://s1.erome.com/1/Abcd1234/clip.jpg",
                EromeRepository.gridArtwork(video));
        assertEquals("https://s1.erome.com/1/Abcd1234/clip.jpg", video.imageUrl);
        assertEquals("https://s1.erome.com/1/Abcd1234/still.jpg", EromeRepository.originalImage(photo));
        assertTrue(page.nextUrl.isEmpty());
    }

    @Test public void missingVideoPosterUsesTheProviderStillName() throws Exception {
        String album = ALBUM.replace(" poster=\"https://s1.erome.com/1/Abcd1234/clip.jpg\"", "");
        NativeContentItem video = EromePages.parseAlbum(album, "https://www.erome.com/a/Abcd1234").items.get(0);
        assertEquals("https://s1.erome.com/1/Abcd1234/clip.jpg", video.imageUrl);
        assertEquals("https://s1.erome.com/1/Abcd1234/clip.jpg", EromeRepository.gridArtwork(video));
        assertEquals("https://s1.erome.com/1/Abcd1234/thumbs/clip.jpg",
                EromeRepository.alternateArtwork(video, EromeRepository.gridArtwork(video)));
    }

    @Test public void lazyPosterAndLazySourceStillFillTheGrid() throws Exception {
        String album = "<html><body><h1 class=\"album-title-page\">Coastal landscape</h1>"
                + "<div class=\"media-group\"><video class=\"player video-js vjs-16-9\" "
                + "data-poster=\"https://s87.erome.com/2257/Abcd1234/Dog8eN5s.jpg\">"
                + "<source data-src=\"https://v87.erome.com/2257/Abcd1234/Dog8eN5s_720p.mp4\" "
                + "type=\"video/mp4\" res=\"720\"></video></div>"
                + "<div class=\"media-group\"><img class=\"img-front\" width=\"1179\" height=\"657\" "
                + "data-src=\"https://s87.erome.com/2257/Abcd1234/Y90g5hUf.jpeg?v=1792147094\">"
                + "</div></body></html>";
        EromePages.AlbumPage page = EromePages.parseAlbum(album, "https://www.erome.com/a/Abcd1234");
        assertEquals(2, page.items.size());
        NativeContentItem video = page.items.get(0);
        NativeContentItem photo = page.items.get(1);
        assertEquals("https://s87.erome.com/2257/Abcd1234/Dog8eN5s.jpg", video.imageUrl);
        assertEquals("https://v87.erome.com/2257/Abcd1234/Dog8eN5s_720p.mp4", video.sourceMediaUrl);
        assertEquals(16f / 9f, video.aspectRatioHint, 0.001f);
        assertEquals("https://s87.erome.com/2257/Abcd1234/Dog8eN5s.jpg",
                EromeRepository.gridArtwork(video));
        assertEquals("https://s87.erome.com/2257/Abcd1234/thumbs/Dog8eN5s.jpg",
                EromeRepository.nextArtwork(video, java.util.Collections.singleton(
                        EromeRepository.gridArtwork(video))));
        assertEquals("https://s87.erome.com/2257/Abcd1234/Y90g5hUf.jpeg?v=1792147094", photo.imageUrl);
        assertEquals("https://s87.erome.com/2257/Abcd1234/Y90g5hUf.jpeg?v=1792147094",
                EromeRepository.gridArtwork(photo));
        NativeContentItem cached = new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Video", "https://www.erome.com/a/Abcd1234#Dog8eN5s",
                "", "", "", "", "", "", 0L, 16f / 9f)
                .withSourceMedia("https://v87.erome.com/2257/Abcd1234/Dog8eN5s_720p.mp4",
                        "https://www.erome.com/a/Abcd1234");
        assertTrue(EromeRepository.missingPoster(java.util.Collections.singletonList(cached)));
        assertEquals("https://s87.erome.com/2257/Abcd1234/Dog8eN5s.jpg",
                EromeRepository.gridArtwork(cached));
        assertFalse(EromeRepository.missingPoster(page.items));
    }

    @Test public void urlsStayOnEromeAndSearchPaginationIsBounded() {
        assertTrue(EromeRepository.isAlbumUrl("https://www.erome.com/a/Abcd1234#clip"));
        assertFalse(EromeRepository.isAlbumUrl("https://www.erome.com/SampleStudio"));
        assertFalse(EromeRepository.isAlbumUrl("https://www.erome.com/search?q=nature"));
        assertFalse(EromeRepository.isAlbumUrl("http://www.erome.com/a/Abcd1234"));
        assertFalse(EromeRepository.isDirectVideo("https://cdn.evil.example/clip.mp4"));
        assertEquals("https://www.erome.com/search?q=coastal+light", EromeRepository.searchUrl(" coastal light ", 1));
        assertTrue(EromeRepository.searchUrl("nature", 2).endsWith("&page=2"));
        assertFalse(EromeRepository.searchUrl("nature", 99).contains("page=99"));
        assertEquals("https://www.erome.com/a/Abcd1234?page=2",
                EromeRepository.sameAlbumPage("https://www.erome.com/a/Abcd1234",
                        "https://www.erome.com/a/Abcd1234?page=2"));
        assertEquals("", EromeRepository.sameAlbumPage("https://www.erome.com/a/Abcd1234",
                "https://www.erome.com/a/Other9999?page=2"));
    }

    @Test public void durationBadgeUsesExistingMetadataAndLeavesOtherGalleriesAlone() throws Exception {
        assertEquals("1:02", GalleryDuration.format("01:02"));
        assertEquals("0:09", GalleryDuration.format("00:09"));
        assertEquals("12:05", GalleryDuration.format("12:05"));
        assertEquals("1:02:03", GalleryDuration.format("1:02:03"));
        assertEquals("1:02:03", GalleryDuration.format("01:02:03"));
        assertEquals("", GalleryDuration.format("OnlyFap"));
        assertEquals("", GalleryDuration.format("Fapello"));
        assertEquals("", GalleryDuration.format("3 pictures · 1 video"));
        assertEquals("", GalleryDuration.format("1:02 extra"));
        assertEquals("", GalleryDuration.format("99"));
        NativeContentItem video = EromePages.parseAlbum(ALBUM, "https://www.erome.com/a/Abcd1234").items.get(0);
        assertEquals("1:02", GalleryDuration.label(video));
        NativeContentItem onlyFap = new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Clip", "https://fapello.com/video/1",
                "", "", "", "", "OnlyFap");
        assertEquals("", GalleryDuration.label(onlyFap));
        NativeContentItem photo = new NativeContentItem(
                NativeContentItem.KIND_IMAGE, "Still", "https://www.erome.com/a/Abcd1234#img-still",
                "", "", "", "", "01:02");
        assertEquals("", GalleryDuration.label(photo));
    }

    @Test public void eromeTilesFollowDisplayedArtworkWithoutFlatteningOtherGalleries() {
        String album = "https://www.erome.com/a/Abcd1234#clip";
        assertEquals(1f, BunkrGalleryAdapter.initialTileAspect(
                album, 16f / 9f, 0f, "https://s1.erome.com/1/Abcd1234/thumbs/clip.jpg"), 0.001f);
        assertEquals(16f / 9f, BunkrGalleryAdapter.initialTileAspect(
                album, 16f / 9f, 0f, "https://s1.erome.com/1/Abcd1234/clip.jpg"), 0.001f);
        assertEquals(0.8f, BunkrGalleryAdapter.initialTileAspect(album, 0.8f, 0.8f, ""), 0.001f);
        assertEquals(1f, BunkrGalleryAdapter.displayedTileAspect(album, 16f / 9f, 250, 250), 0.001f);
        assertEquals(0.8f, BunkrGalleryAdapter.displayedTileAspect(album, 16f / 9f, 800, 1000), 0.001f);
        assertEquals(16f / 9f, BunkrGalleryAdapter.displayedTileAspect(album, 9f / 16f, 1920, 1080), 0.001f);
        assertEquals(2.8f, BunkrGalleryAdapter.displayedTileAspect(
                "https://fapello.com/model/photo", 2.8f, 250, 250), 0.001f);
        assertEquals(9f / 16f, BunkrGalleryAdapter.initialTileAspect(
                "https://fapello.com/model/photo", 9f / 16f, 0f,
                "https://fapello.com/thumbs/photo.jpg"), 0.001f);
    }
}
