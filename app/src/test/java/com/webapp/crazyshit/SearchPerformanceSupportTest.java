package com.webapp.crazyshit;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class SearchPerformanceSupportTest {
    @Test public void searchResultCacheReturnsDefensiveCopiesForACompletedQuery() {
        SearchResultCache cache = new SearchResultCache();
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Result",
                "https://example.com/result",
                "",
                "",
                "Test",
                ""
        );

        cache.put("Shows:CrazyShit", "car crash", Collections.singletonList(item));
        List<NativeContentItem> first = cache.get("Shows:CrazyShit", "car crash");
        List<NativeContentItem> second = cache.get("Shows:CrazyShit", "  CAR   CRASH  ");

        assertEquals(1, first.size());
        assertEquals(item.url, first.get(0).url);
        assertEquals(1, second.size());
        assertNotSame(first, second);
        first.clear();
        assertEquals(1, cache.get("Shows:CrazyShit", "car crash").size());
        assertNull(cache.get("Shows:CrazyShit", "x"));
    }

    @Test public void cacheDoesNotStoreEmptyRemoteMisses() {
        SearchResultCache cache = new SearchResultCache();
        cache.put("XMegaDrive", "sample", Collections.emptyList());
        assertNull(cache.get("XMegaDrive", "sample"));
    }

    @Test public void searchDiagnosticsExposeTimingsWithoutQueryTextOrSecrets() {
        LinkedHashMap<String, Long> timings = new LinkedHashMap<>();
        timings.put("CrazyShit", 210L);
        timings.put("Bunkr", 0L);
        LinkedHashSet<String> cached = new LinkedHashSet<>();
        cached.add("Bunkr");

        String report = SearchPerformanceDiagnostics.report(
                "Shows",
                17,
                350L,
                210L,
                640L,
                12,
                timings,
                cached
        );

        assertTrue(report.contains("Scope: Shows"));
        assertTrue(report.contains("Query length: 17"));
        assertTrue(report.contains("First result: 210 ms"));
        assertTrue(report.contains("Complete: 640 ms"));
        assertTrue(report.contains("- Bunkr: 0 ms (cache)"));
        assertTrue(report.contains("Query text, cookies, auth headers, signed URLs, and tokens are not included."));
        assertFalse(report.contains("secret query"));
    }
}
