package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ShowsEpisodeRangesTest {
    @Test
    public void largeSourceListUsesStableTwentyFiveItemRanges() {
        List<NativeContentItem> items = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
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

        assertEquals(0, ShowsEpisodeRanges.startForIndex(24, items.size()));
        assertEquals(25, ShowsEpisodeRanges.startForIndex(25, items.size()));
        assertEquals(50, ShowsEpisodeRanges.startForIndex(60, items.size()));
        assertEquals("26–50", ShowsEpisodeRanges.label(25, items.size()));
        assertEquals("51–61", ShowsEpisodeRanges.label(50, items.size()));
        assertEquals(25, ShowsEpisodeRanges.slice(items, 25).size());
        assertEquals(11, ShowsEpisodeRanges.slice(items, 50).size());
        assertEquals(3, ShowsEpisodeRanges.starts(items.size()).size());
    }
}
