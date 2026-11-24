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
