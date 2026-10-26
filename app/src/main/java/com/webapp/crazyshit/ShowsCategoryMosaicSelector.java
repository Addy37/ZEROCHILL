package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

final class ShowsCategoryMosaicSelector {
    static final long SESSION_SEED = System.nanoTime() ^ System.currentTimeMillis();

    private ShowsCategoryMosaicSelector() {
    }

    static List<NativeContentItem> select(
            List<NativeContentItem> items,
            String categoryUrl,
            long seed,
            int limit
    ) {
        LinkedHashMap<String, NativeContentItem> unique = new LinkedHashMap<>();
        if (items != null) {
            for (NativeContentItem item : items) {
                if (item == null || !ShowsEpisodePlayback.playable(item)) continue;
                String image = item.imageUrl == null ? "" : item.imageUrl.trim();
                if (image.isEmpty()) continue;
                unique.putIfAbsent(image, item);
            }
        }
        ArrayList<NativeContentItem> pool = new ArrayList<>(unique.values());
        long categorySeed = seed ^ (categoryUrl == null ? 0L : categoryUrl.hashCode() * 0x9E3779B97F4A7C15L);
        Collections.shuffle(pool, new Random(categorySeed));
        int take = Math.min(Math.max(0, limit), pool.size());
        return new ArrayList<>(pool.subList(0, take));
    }
}
