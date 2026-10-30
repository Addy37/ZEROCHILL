package com.webapp.crazyshit;

import java.util.*;

/** Already-admitted future clips only; all exclusions are rechecked on restore. No disk writes. */
final class ShitTokWarmCategoryCache {
    static final int MAX_PER_CATEGORY = 10;
    static final long MAX_AGE_MS = 10 * 60_000L;
    static final class Entry {
        final NativeContentItem item;
        final CrazyShitRepository.StreamInfo stream;
        final long at;
        Entry(NativeContentItem item, CrazyShitRepository.StreamInfo stream, long at) {
            this.item = item; this.stream = stream; this.at = at;
        }
    }
    private final EnumMap<ContentPreference, List<Entry>> categories = new EnumMap<>(ContentPreference.class);
    void save(ContentPreference mode, List<NativeContentItem> future,
              Map<String, CrazyShitRepository.StreamInfo> streams, long now) {
        if (mode == null || mode == ContentPreference.ALL) return;
        ArrayList<Entry> entries = new ArrayList<>();
        for (NativeContentItem item : future) {
            CrazyShitRepository.StreamInfo stream = streams.get(item.url);
            if (stream == null || !ShitTokOrientation.eligible(item.aspectRatioHint)) continue;
            entries.add(new Entry(item, stream, now));
            if (entries.size() == MAX_PER_CATEGORY) break;
        }
        // An empty visit must not destroy an earlier still-warm category.
        if (!entries.isEmpty()) categories.put(mode, entries);
    }
    List<Entry> take(ContentPreference mode, long now) {
        List<Entry> saved = categories.remove(mode);
        ArrayList<Entry> result = new ArrayList<>();
        if (saved != null) for (Entry entry : saved)
            if (now - entry.at <= MAX_AGE_MS) result.add(entry);
        return result;
    }
}
