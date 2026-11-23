package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Five video queues, not five players: each tab resumes its own clip and position. */
public class ShitTokFeedSessionStoreTest {
    private static NativeContentItem clip(String id) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, id,
                "https://example.test/" + id, "", "", "Test", "", "", "", 0L, .5f);
    }
    private static CrazyShitRepository.StreamInfo stream(NativeContentItem item) {
        return new CrazyShitRepository.StreamInfo(item.url + ".mp4", item.url, "clip");
    }

    @Test public void eachOfFiveTabsRetainsOwnVideoTimestampPauseAndQueue() {
        ShitTokFeedSessionStore sessions = new ShitTokFeedSessionStore();
        ContentPreference[] tabs = {ContentPreference.ALL, ContentPreference.WOMEN,
                ContentPreference.MEN, ContentPreference.TRANS_WOMEN, ContentPreference.GROUPS};
        for (int i = 0; i < tabs.length; i++) {
            NativeContentItem previous = clip("prev-" + i);
            NativeContentItem selected = clip("selected-" + i);
            NativeContentItem upcoming = clip("next-" + i);
            Map<String, CrazyShitRepository.StreamInfo> sources = new HashMap<>();
            sources.put(selected.url, stream(selected));
            sessions.save(ShitTokSourceMode.ALL, tabs[i],
                    Arrays.asList(previous, selected, upcoming), 1,
                    11000L + 2500L * i, i % 2 == 0, sources);
        }
        for (int i = 0; i < tabs.length; i++) {
            ShitTokFeedSessionStore.Session saved =
                    sessions.get(ShitTokSourceMode.ALL, tabs[i]);
            assertNotNull(saved);
            assertEquals("https://example.test/selected-" + i, saved.selectedUrl);
            assertEquals(1, saved.selectedIndex);
            assertEquals(11000L + 2500L * i, saved.positionMs);
            assertEquals(i % 2 == 0, saved.paused);
            assertEquals(3, saved.queue.size());
            assertEquals("https://example.test/next-" + i, saved.queue.get(2).url);
            assertEquals(saved.selectedUrl + ".mp4", saved.streams.get(saved.selectedUrl).mediaUrl);
        }
    }

    @Test public void hundredPlusVideosKeepTheExactSelectedClipAndPriorPages() {
        ShitTokFeedSessionStore sessions = new ShitTokFeedSessionStore();
        List<NativeContentItem> pages = new ArrayList<>();
        for (int i = 0; i < 230; i++) pages.add(clip("large-" + i));
        sessions.save(ShitTokSourceMode.ALL, ContentPreference.WOMEN,
                pages, 200, 87654L, true, Collections.emptyMap());
        ShitTokFeedSessionStore.Session saved =
                sessions.get(ShitTokSourceMode.ALL, ContentPreference.WOMEN);
        assertEquals(ShitTokFeedSessionStore.MAX_ITEMS, saved.queue.size());
        assertEquals(pages.get(200), saved.queue.get(saved.selectedIndex));
        assertTrue(saved.selectedIndex > 0);
        assertEquals(87654L, saved.positionMs);
    }

    @Test public void aDeepReturnKeepsNextStreamsWarmBeforeOlderHistory() {
        ShitTokFeedSessionStore sessions = new ShitTokFeedSessionStore();
        List<NativeContentItem> pages = new ArrayList<>();
        Map<String, CrazyShitRepository.StreamInfo> streams = new HashMap<>();
        for (int i = 0; i < 150; i++) {
            NativeContentItem page = clip("page-" + i);
            pages.add(page);
            streams.put(page.url, stream(page));
        }
        sessions.save(ShitTokSourceMode.ALL, ContentPreference.GROUPS,
                pages, 100, 54321L, false, streams);
        ShitTokFeedSessionStore.Session saved =
                sessions.get(ShitTokSourceMode.ALL, ContentPreference.GROUPS);
        assertEquals(ShitTokFeedSessionStore.MAX_ITEMS > pages.size() ? pages.size()
                : ShitTokFeedSessionStore.MAX_ITEMS, saved.queue.size());
        assertNotNull(saved.streams.get(pages.get(100).url));
        assertNotNull(saved.streams.get(pages.get(101).url));
        assertNotNull(saved.streams.get(pages.get(110).url));
        assertNotNull(saved.streams.get(pages.get(99).url));
        assertTrue(saved.streams.size() <= 32);
    }

    @Test public void unactivatedOrEmptyTabCannotOverwriteItsLastValidCheckpoint() {
        ShitTokFeedSessionStore sessions = new ShitTokFeedSessionStore();
        NativeContentItem saved = clip("last-valid-ladies");
        sessions.save(ShitTokSourceMode.ALL, ContentPreference.WOMEN,
                Collections.singletonList(saved), 0, 1000L, false, Collections.emptyMap());
        sessions.save(ShitTokSourceMode.ALL, ContentPreference.WOMEN,
                Collections.emptyList(), 0, 0L, false, Collections.emptyMap());
        assertEquals(saved.url, sessions.get(ShitTokSourceMode.ALL, ContentPreference.WOMEN).selectedUrl);
        assertNull(sessions.get(ShitTokSourceMode.ALL, ContentPreference.MEN));
        sessions.clear();
        assertNull(sessions.get(ShitTokSourceMode.ALL, ContentPreference.WOMEN));
    }

    @Test public void tabDirectionMatchesVisibleHeaderAndDoesNotSwipePlayerGestures() {
        assertEquals(1, ChaosFeedView.horizontalPreferenceDirection(ContentPreference.ALL, ContentPreference.WOMEN));
        assertEquals(1, ChaosFeedView.horizontalPreferenceDirection(ContentPreference.WOMEN, ContentPreference.GROUPS));
        assertEquals(-1, ChaosFeedView.horizontalPreferenceDirection(ContentPreference.GROUPS, ContentPreference.MEN));
        assertEquals(-1, ChaosFeedView.horizontalPreferenceDirection(ContentPreference.WOMEN, ContentPreference.ALL));
    }
}
