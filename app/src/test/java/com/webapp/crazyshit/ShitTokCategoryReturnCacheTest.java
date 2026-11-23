package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Per-tab state must not turn a return into another random playlist. */
public class ShitTokCategoryReturnCacheTest {
    private static NativeContentItem clip(String id) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, id,
                "https://example.test/" + id, "", "", "Test", "", "", "", 0L, .5f);
    }
    private static CrazyShitRepository.StreamInfo stream(NativeContentItem item) {
        return new CrazyShitRepository.StreamInfo(item.url, item.url + ".mp4", "clip");
    }
    @Test public void returnsLastViewedClipAndQueuedSuccessorsPerCategory() {
        ShitTokCategoryReturnCache cache = new ShitTokCategoryReturnCache();
        NativeContentItem chaos = clip("chaos-wide"), chaosNext = clip("chaos-next");
        NativeContentItem ladies = clip("ladies");
        Map<String, CrazyShitRepository.StreamInfo> streams = new HashMap<>();
        streams.put(chaos.url, stream(chaos)); streams.put(chaosNext.url, stream(chaosNext));
        streams.put(ladies.url, stream(ladies));
        cache.remember(ShitTokSourceMode.ALL, ContentPreference.ALL, chaos,
                Collections.singletonList(chaosNext), streams, 10L);
        cache.remember(ShitTokSourceMode.ALL, ContentPreference.WOMEN, ladies,
                Collections.emptyList(), streams, 20L);
        ShitTokCategoryReturnCache.Checkpoint backToChaos =
                cache.take(ShitTokSourceMode.ALL, ContentPreference.ALL, 30L);
        assertEquals(chaos.url, backToChaos.selectedUrl);
        assertEquals(Arrays.asList(chaos, chaosNext),
                Arrays.asList(backToChaos.entries.get(0).item, backToChaos.entries.get(1).item));
        assertSame(streams.get(chaos.url), backToChaos.entries.get(0).stream);
        assertEquals(ladies.url, cache.take(ShitTokSourceMode.ALL,
                ContentPreference.WOMEN, 30L).selectedUrl);
        // Reading the bookmark must not consume it before a quick second tab switch.
        assertEquals(chaos.url, cache.take(ShitTokSourceMode.ALL,
                ContentPreference.ALL, 30L).selectedUrl);
    }

    @Test public void returnsOnlyValidRecentCheckpointAndLimitsNextQueue() {
        ShitTokCategoryReturnCache cache = new ShitTokCategoryReturnCache();
        NativeContentItem current = clip("current");
        ArrayList<NativeContentItem> future = new ArrayList<>();
        Map<String, CrazyShitRepository.StreamInfo> streams = new HashMap<>();
        streams.put(current.url, stream(current));
        for (int i = 0; i < 40; i++) {
            NativeContentItem item = clip("future-" + i);
            future.add(item); streams.put(item.url, stream(item));
        }
        cache.remember(ShitTokSourceMode.ALL, ContentPreference.MEN,
                current, future, streams, 0L);
        ShitTokCategoryReturnCache.Checkpoint result =
                cache.take(ShitTokSourceMode.ALL, ContentPreference.MEN, 1L);
        assertEquals(1 + ShitTokCategoryReturnCache.MAX_FUTURE, result.entries.size());
        cache.remember(ShitTokSourceMode.ALL, ContentPreference.MEN,
                current, future, streams, 0L);
        assertNull(cache.take(ShitTokSourceMode.ALL, ContentPreference.MEN,
                ShitTokCategoryReturnCache.MAX_AGE_MS + 1L));
        cache.remember(ShitTokSourceMode.ALL, ContentPreference.MEN,
                current, future, streams, 100L);
        cache.clear();
        assertNull(cache.take(ShitTokSourceMode.ALL, ContentPreference.MEN, 101L));
    }
}
