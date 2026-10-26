package com.webapp.crazyshit;

import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ShowsWatchSessionTest {
    private NativeContentItem episode(String title, String id) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, title,
                "https://example.test/" + id, "https://example.test/" + id + ".jpg", "", "", "");
    }

    @Test public void attachRoundTripsContextWithoutCurrentInRelated() {
        NativeContentItem first = episode("Episode 1", "one");
        NativeContentItem second = episode("Episode 2", "two");
        Intent intent = ShowsWatchSession.attach(new Intent(),
                Arrays.asList(first, first, second), true, "Season 1");
        ShowsWatchSession session = ShowsWatchSession.from(intent);
        assertTrue(intent.getBooleanExtra(VideoDetailActivity.EXTRA_SHOWS_ORIGIN, false));
        assertEquals("Season 1", session.label);
        assertEquals(2, session.items.size());
        assertEquals(Collections.singletonList(second.url),
                Collections.singletonList(session.related(first.url).get(0).url));
        assertEquals(second.url, session.next(first.url).url);
        assertNull(session.next(second.url));
    }

    @Test public void numberedEpisodeWinsOverReverseDisplayOrderAndStandaloneFollowsList() {
        NativeContentItem third = episode("S1E3", "three");
        NativeContentItem first = episode("S1E1", "one");
        NativeContentItem second = episode("S1E2", "two");
        ShowsWatchSession episodes = ShowsWatchSession.from(ShowsWatchSession.attach(new Intent(),
                Arrays.asList(third, first, second), true, "Series"));
        assertEquals(second.url, episodes.next(first.url).url);
        assertEquals(third.url, episodes.next(second.url).url);
        ShowsWatchSession standalone = ShowsWatchSession.from(ShowsWatchSession.attach(new Intent(),
                Arrays.asList(first, second), false, "More"));
        assertEquals(second.url, standalone.next(first.url).url);
        assertEquals(first.url, standalone.next(second.url).url);
        assertNull(standalone.next(second.url, new java.util.HashSet<>(Arrays.asList(first.url, second.url))));
    }

    @Test public void longSeriesRetainsSelectedEpisodeAndCanCrossIntoNextSeason() {
        java.util.List<NativeContentItem> many = new java.util.ArrayList<>();
        for (int i = 1; i <= 90; i++) many.add(episode("S1E" + i, "s1-" + i));
        NativeContentItem nextSeason = episode("S2E1", "s2-1");
        many.add(nextSeason);
        Intent intent = new Intent().putExtra(PlayerActivity.EXTRA_PAGE_URL, many.get(49).url);
        ShowsWatchSession session = ShowsWatchSession.from(
                ShowsWatchSession.attach(intent, many, true, "Series"));
        assertTrue(session.items.stream().anyMatch(item -> item.url.equals(many.get(49).url)));
        assertEquals(many.get(50).url, session.next(many.get(49).url).url);
        assertEquals(nextSeason.url, session.next(many.get(89).url).url);
        assertTrue(session.related(many.get(49).url).size() <= 24);
    }
}
