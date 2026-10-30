package com.webapp.crazyshit;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ShitTokWarmCategoryCacheTest {
    @Test public void warmClipsAndResolvedStreamsAreBoundedIsolatedAndExpire() {
        ShitTokWarmCategoryCache cache = new ShitTokWarmCategoryCache();
        List<NativeContentItem> clips = new ArrayList<>();
        Map<String, CrazyShitRepository.StreamInfo> streams = new HashMap<>();
        for (int i = 0; i < 40; i++) {
            NativeContentItem item = ShitTokFilteredInventoryTest.clip("Alice", 6, i);
            clips.add(item); streams.put(item.url, new CrazyShitRepository.StreamInfo(item.url, item.url, "clip"));
        }
        cache.save(ContentPreference.WOMEN, clips, streams, 0);
        assertTrue(cache.take(ContentPreference.MEN, 1).isEmpty());
        List<ShitTokWarmCategoryCache.Entry> warm = cache.take(ContentPreference.WOMEN, 1);
        assertEquals(10, warm.size()); assertSame(streams.get(clips.get(0).url), warm.get(0).stream);
        assertTrue(cache.take(ContentPreference.WOMEN, 1).isEmpty());
        cache.save(ContentPreference.WOMEN, clips, streams, 0);
        assertTrue(cache.take(ContentPreference.WOMEN, ShitTokWarmCategoryCache.MAX_AGE_MS + 1).isEmpty());
        cache.save(ContentPreference.ALL, clips, streams, 0);
        assertTrue(cache.take(ContentPreference.ALL, 1).isEmpty());
    }
}
