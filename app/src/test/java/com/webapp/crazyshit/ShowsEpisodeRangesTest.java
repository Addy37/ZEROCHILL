package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ShowsEpisodeRangesTest {
    @Test
    public void fullSourceListUsesTenItemRangesAndFinalPartialRange() {
        List<NativeContentItem> items = new ArrayList<>();
        for (int i = 0; i < 124; i++) {
            items.add(new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    "Source title " + i,
                    "https://example.com/" + i,
                    "https://example.com/" + i + ".jpg",
                    "",
                    "",
                    ""
            ));
        }

        assertEquals(0, ShowsEpisodeRanges.startForIndex(9, items.size()));
        assertEquals(10, ShowsEpisodeRanges.startForIndex(10, items.size()));
        assertEquals(120, ShowsEpisodeRanges.startForIndex(123, items.size()));
        assertEquals("11–20", ShowsEpisodeRanges.label(10, items.size()));
        assertEquals("121–124", ShowsEpisodeRanges.label(120, items.size()));
        assertEquals(10, ShowsEpisodeRanges.slice(items, 10).size());
        assertEquals(4, ShowsEpisodeRanges.slice(items, 120).size());
        assertEquals(items.get(123), ShowsEpisodeRanges.slice(items, 123).get(3));
        assertEquals(13, ShowsEpisodeRanges.starts(items.size()).size());
    }

    @Test public void searchIndexAndPreviousNextBoundaries() {
        assertEquals(70, ShowsEpisodeRanges.startForIndex(76, 124));
        assertEquals("71–80", ShowsEpisodeRanges.label(76, 124));
        assertEquals(false, ShowsEpisodeRanges.hasPrevious(0, 124));
        assertEquals(true, ShowsEpisodeRanges.hasNext(0, 124));
        assertEquals(true, ShowsEpisodeRanges.hasPrevious(10, 124));
        assertEquals(true, ShowsEpisodeRanges.hasNext(110, 124));
        assertEquals(true, ShowsEpisodeRanges.hasPrevious(120, 124));
        assertEquals(false, ShowsEpisodeRanges.hasNext(120, 124));
        assertEquals(false, ShowsEpisodeRanges.hasNext(0, 10));
    }

    @Test public void loadingStartsBeforeLastRangeAndStopsWhenBufferIsReady() {
        assertEquals(false, ShowsEpisodeRanges.nearLoadedEnd(0, 40));
        assertEquals(true, ShowsEpisodeRanges.nearLoadedEnd(10, 40));
        assertEquals(true, ShowsEpisodeRanges.nearLoadedEnd(20, 40));
        assertEquals(false, ShowsEpisodeRanges.nearLoadedEnd(10, 60));
        assertEquals(true, ShowsEpisodeRanges.nearLoadedEnd(100, 124));
        assertEquals(false, ShowsEpisodeRanges.nearLoadedEnd(90, 124));
        assertEquals(false, ShowsEpisodeRanges.nearLoadedEnd(0, 0));
    }
}
