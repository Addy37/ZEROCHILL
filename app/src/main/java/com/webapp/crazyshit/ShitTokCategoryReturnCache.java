package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory per-category resume checkpoint. Does not alter the global watched history. */
final class ShitTokCategoryReturnCache {
    static final int MAX_FUTURE = 10;
    static final long MAX_AGE_MS = 10 * 60_000L;
    static final class Entry {
        final NativeContentItem item;
        final CrazyShitRepository.StreamInfo stream;
        Entry(NativeContentItem item, CrazyShitRepository.StreamInfo stream) {
            this.item = item;
            this.stream = stream;
        }
    }
    static final class Checkpoint {
        final String selectedUrl;
        final List<Entry> entries;
        final long at;
        Checkpoint(String selectedUrl, List<Entry> entries, long at) {
            this.selectedUrl = selectedUrl;
            this.entries = entries;
            this.at = at;
        }
    }
    private final Map<String, Checkpoint> saved = new LinkedHashMap<>();

    void remember(ShitTokSourceMode sources, ContentPreference preference,
            NativeContentItem selected, List<NativeContentItem> future,
            Map<String, CrazyShitRepository.StreamInfo> streams, long now) {
        if (sources == null || preference == null || selected == null) return;
        ArrayList<Entry> entries = new ArrayList<>();
        // The selected video comes first, including horizontal clips in Chaos.
        entries.add(new Entry(selected, streams.get(selected.url)));
        for (NativeContentItem candidate : future) {
            if (candidate == null || candidate.url == null || candidate.url.equals(selected.url)) continue;
            if (entries.size() > MAX_FUTURE) break;
            CrazyShitRepository.StreamInfo stream = streams.get(candidate.url);
            if (stream != null) entries.add(new Entry(candidate, stream));
        }
        saved.put(sources.key(preference), new Checkpoint(selected.url, entries, now));
    }

    Checkpoint take(ShitTokSourceMode sources, ContentPreference preference, long now) {
        if (sources == null || preference == null) return null;
        String key = sources.key(preference);
        Checkpoint checkpoint = saved.get(key);
        if (checkpoint == null) return null;
        if (now - checkpoint.at > MAX_AGE_MS) {
            saved.remove(key);
            return null;
        }
        // A rapid A-B-A-C tap must not consume A's bookmark before A ever plays.
        return checkpoint;
    }

    void clear() { saved.clear(); }
}
