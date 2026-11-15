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

import java.io.IOException;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class BunkrIndexSearchTest {
    @Test
    public void groupsFileHitsByAlbumWithoutChangingGalleryLinks() throws Exception {
        String payload = "{\"page\":1,\"results\":["
                + "{\"album_slug\":\"album_123\",\"album_name\":\"Sample Creator\","
                + "\"file_count\":12,\"thumbnail\":\"https://static.scdn.st/thumbs/a.png\","
                + "\"file_url\":\"https://bunkr.cr/f/some_file\"},"
                + "{\"album_slug\":\"album_123\",\"album_name\":\"Sample Creator\","
                + "\"file_count\":12},"
                + "{\"album_slug\":\"second_456\",\"album_name\":\"Second Album\","
                + "\"file_count\":3}]}";
        List<NativeContentItem> results = BunkrIndexSearch.parseResults(payload);
        assertEquals(2, results.size());
        assertEquals(NativeContentItem.KIND_SERIES, results.get(0).kind);
        assertEquals("Sample Creator", results.get(0).title);
        assertEquals("https://bunkr.cr/a/album_123", results.get(0).url);
        assertEquals("https://static.scdn.st/thumbs/a.png", results.get(0).imageUrl);
        assertEquals("12 files", results.get(0).description);
        assertEquals("https://bunkr.cr/a/second_456", results.get(1).url);
        assertTrue(BunkrRepository.isAlbumUrl(results.get(1).url));
    }

    @Test
    public void rejectsUnsafeAlbumIdsAndNonHttpsArtwork() throws Exception {
        String payload = "{\"page\":1,\"results\":["
                + "{\"album_slug\":\"../evil\",\"album_name\":\"Unsafe\"},"
                + "{\"album_slug\":\"safe_123\",\"album_name\":\"\","
                + "\"thumbnail\":\"http://example.com/tracker.png\"},"
                + "{\"album_slug\":\"other_123\",\"album_name\":\"Good\","
                + "\"thumbnail\":\"javascript:alert(1)\"}]}";
        List<NativeContentItem> results = BunkrIndexSearch.parseResults(payload);
        assertEquals(2, results.size());
        assertEquals("Bunkr album safe_123", results.get(0).title);
        assertTrue(results.get(0).imageUrl.isEmpty());
        assertTrue(results.get(1).imageUrl.isEmpty());
    }

    @Test
    public void preservesEmptyResultsAsAnEmptySearch() throws Exception {
        assertTrue(BunkrIndexSearch.parseResults(
                "{\"page\":1,\"pages\":0,\"total\":0,\"results\":[]}"
        ).isEmpty());
    }

    @Test
    public void rejectsUnexpectedResponsesRatherThanReportingNoMatches() {
        assertThrows(IOException.class, () -> BunkrIndexSearch.parseResults("<html>Blocked</html>"));
        assertThrows(IOException.class, () -> BunkrIndexSearch.parseResults("{\"message\":\"oops\"}"));
    }

    @Test
    public void interruptedReadReachesTheCheckedFallbackPath() {
        org.jsoup.Connection.Response response = (org.jsoup.Connection.Response)
                java.lang.reflect.Proxy.newProxyInstance(org.jsoup.Connection.Response.class.getClassLoader(),
                        new Class<?>[]{org.jsoup.Connection.Response.class}, (proxy, method, args) -> {
                            if (method.getName().equals("readBody")) throw new IOException("Interrupted read");
                            throw new AssertionError("Unexpected response method: " + method.getName());
                        });
        IOException error = assertThrows(IOException.class, () -> BunkrIndexSearch.parseResponse(response));
        assertEquals("Interrupted read", error.getMessage());
    }

    @Test
    public void encodesQueriesAndBoundsPage() {
        String url = BunkrIndexSearch.searchUrl("  sample + test  ", -5);
        assertTrue(url.startsWith("https://bunkrsearch.link/api/search?q="));
        assertTrue(url.contains("sample+%2B+test"));
        assertTrue(url.endsWith("&page=1"));
        assertTrue(BunkrIndexSearch.searchUrl("demo", 2).endsWith("&page=2"));
        assertFalse(BunkrIndexSearch.searchUrl("demo", 2).contains("balbums.st"));
    }
}
