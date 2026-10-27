package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/** Bounded, loaded-only detail artwork choices; source and player order are untouched. */
final class ShowsDetailHeroSelector {
    private ShowsDetailHeroSelector() { }

    static List<NativeContentItem> candidates(List<NativeContentItem> items, Random random) {
        LinkedHashMap<String, NativeContentItem> unique = new LinkedHashMap<>();
        if (items != null) for (NativeContentItem item : items) {
            if (item == null || !ShowsEpisodePlayback.playable(item)) continue;
            String image = item.imageUrl == null ? "" : item.imageUrl.trim();
            if (!image.isEmpty()) unique.putIfAbsent(image, item);
        }
        ArrayList<NativeContentItem> candidates = new ArrayList<>(unique.values());
        Collections.shuffle(candidates, random);
        return candidates;
    }

    static int next(int current, int size, Random random) {
        if (size < 2) return -1;
        if (current < 0 || current >= size) return random.nextInt(size);
        int index = random.nextInt(size - 1);
        return index >= current ? index + 1 : index;
    }
}
