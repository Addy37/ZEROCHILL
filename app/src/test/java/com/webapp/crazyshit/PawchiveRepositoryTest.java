package com.webapp.crazyshit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PawchiveRepositoryTest {
    @Test public void constructionDoesNotFetchAndSearchUsesEncodedFiftyItemOffsets() throws Exception {
        java.util.List<String> requests = new java.util.ArrayList<>();
        PawchiveRepository repository = new PawchiveRepository(route -> {
            requests.add(route);
            return "[]";
        });
        assertTrue(requests.isEmpty());
        repository.recentPosts("a & b", 50);
        repository.creatorPosts("fanbox", "123", null, 100);
        assertEquals(java.util.Arrays.asList("/posts?o=50&q=a+%26+b",
                "/fanbox/user/123?o=100"), requests);
    }

    @Test public void creatorIdentityIncludesServiceAndId() throws Exception {
        List<PawchiveRepository.Creator> creators = PawchiveRepository.parseCreators(
                "[{\"service\":\"fanbox\",\"id\":\"1\",\"name\":\"Artist\",\"favorited\":4},"
                + "{\"service\":\"patreon\",\"id\":\"1\",\"name\":\"Artist\",\"favorited\":9},"
                + "{\"service\":\"fanbox\",\"id\":\"1\",\"name\":\"Renamed\"}]");
        assertEquals(2, creators.size());
        List<PawchiveRepository.Creator> ranked =
                PawchiveRepository.creatorShelf(creators, "artist", 0, 16);
        assertEquals("patreon/1", ranked.get(0).key());
        assertEquals("fanbox/1", ranked.get(1).key());
        assertEquals("https://pawchive.pw/fanbox/user/1", ranked.get(1).profileUrl());
        assertEquals(1, PawchiveRepository.creatorShelf(creators, "artist", 1, 16).size());
    }

    @Test public void creatorPostsCannotMixAnotherCreatorOrService() throws Exception {
        PawchiveRepository.PostPage result = PawchiveRepository.parsePosts(
                "[{\"service\":\"fanbox\",\"user\":\"1\",\"id\":\"2\",\"title\":\"Drawing\"},"
                + "{\"service\":\"fanbox\",\"user\":\"2\",\"id\":\"2\"},"
                + "{\"service\":\"patreon\",\"user\":\"1\",\"id\":\"2\"},"
                + "{\"service\":\"fanbox\",\"user\":\"1\",\"id\":\"2\"}]",
                "fanbox", "1");
        assertEquals(1, result.posts.size());
        assertFalse(result.hasMore);
        assertEquals("https://pawchive.pw/fanbox/user/1/post/2", result.posts.get(0).postUrl());
    }

    @Test public void paginationUsesRawPageSizeEvenWithDuplicateRecords() throws Exception {
        String row = "{\"service\":\"fanbox\",\"user\":\"1\",\"id\":\"2\"}";
        String body = "[" + String.join(",", java.util.Collections.nCopies(50, row)) + "]";
        PawchiveRepository.PostPage page = PawchiveRepository.parsePosts(body, "fanbox", "1");
        assertEquals(1, page.posts.size());
        assertTrue(page.hasMore);
    }

    @Test public void mediaAllowsOnlyProviderRelativeImagesAndVideos() throws Exception {
        org.json.JSONObject json = new org.json.JSONObject(
                "{\"file\":{\"name\":\"photo.jpg\",\"path\":\"/ab/cd/photo.jpg\"},"
                + "\"attachments\":[{\"name\":\"clip.mp4\",\"path\":\"/ab/cd/clip.mp4\"},"
                + "{\"name\":\"duplicate.jpg\",\"path\":\"/ab/cd/photo.jpg\"},"
                + "{\"name\":\"unsafe.jpg\",\"path\":\"https://example.com/unsafe.jpg\"},"
                + "{\"name\":\"traversal.jpg\",\"path\":\"/../private.jpg\"},"
                + "{\"name\":\"archive.zip\",\"path\":\"/ab/cd/archive.zip\"}]}");
        List<PawchiveRepository.Media> media = PawchiveRepository.parseMedia(json);
        assertEquals(2, media.size());
        assertEquals("https://file.pawchive.pw/data/ab/cd/photo.jpg", media.get(0).url);
        assertFalse(media.get(0).video);
        assertTrue(media.get(1).video);
    }

    @Test public void nativeGalleryUsesExactProfileAndTypedMediaWithoutCrossSourceLookup() throws Exception {
        java.util.List<String> requests = new java.util.ArrayList<>();
        PawchiveRepository repository = new PawchiveRepository(route -> {
            requests.add(route);
            return "[{\"service\":\"fanbox\",\"user\":\"1\",\"id\":\"2\","
                    + "\"title\":\"Artwork\",\"file\":{\"path\":\"/ab/cd/photo.jpg\"},"
                    + "\"attachments\":[{\"path\":\"/ab/cd/clip.mp4\"}]}]";
        });
        PawchiveRepository.Creator creator = PawchiveRepository.profile(
                "https://pawchive.pw/fanbox/user/1", "Artist");
        NativeContentItem card = PawchiveRepository.creatorItem(creator);
        assertEquals("pawchive:fanbox/1", CreatorFavoriteStore.key(card));
        assertEquals(creator.profileUrl(), NativeFeedBrowserActivity.creatorProfileHint(card));
        PawchiveRepository.GalleryPage page = repository.galleryPage(creator, 50);
        assertEquals(java.util.Collections.singletonList("/fanbox/user/1?o=50"), requests);
        assertEquals(2, page.items.size());
        assertTrue(page.items.get(0).isImage());
        assertTrue(page.items.get(1).isVideo());
        assertEquals(creator.profileUrl(), page.items.get(0).uploader);
        assertEquals(creator.profileUrl(), OnlyFapVideoMetadata.creator(page.items.get(1)).url);
        assertFalse(page.hasMore);
    }

    @Test public void directMediaRejectsForeignHostsAndUnsupportedPaths() {
        assertTrue(PawchiveRepository.isDirectMedia("https://file.pawchive.pw/data/ab/cd/photo.jpg"));
        assertFalse(PawchiveRepository.isDirectMedia("https://file.pawchive.pw.evil.test/data/ab/cd/photo.jpg"));
        assertFalse(PawchiveRepository.isDirectMedia("https://file.pawchive.pw/data/../photo.jpg"));
        assertFalse(PawchiveRepository.isDirectMedia("https://file.pawchive.pw/data/ab/cd/archive.zip"));
    }

    @Test(expected = IOException.class)
    public void postDetailRejectsDifferentIdentity() throws Exception {
        PawchiveRepository repo = new PawchiveRepository(route ->
                "{\"service\":\"fanbox\",\"user\":\"other\",\"id\":\"2\"}");
        repo.postMedia(new PawchiveRepository.Post("fanbox", "1", "2", "Test"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnalignedOffset() {
        PawchiveRepository.pageRoute("/posts", "artist", 16);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPathInjection() {
        PawchiveRepository.creatorRoute("fanbox", "../other");
    }

    @Test(expected = IOException.class)
    public void invalidJsonIsNotAnEmptySuccess() throws Exception {
        PawchiveRepository.parseCreators("<html>Unavailable</html>");
    }

    @Test(expected = IOException.class)
    public void entirelyMismatchedPageFailsClosed() throws Exception {
        PawchiveRepository.parsePosts("[{\"service\":\"fanbox\",\"user\":\"2\",\"id\":\"1\"}]",
                "fanbox", "1");
    }
}
