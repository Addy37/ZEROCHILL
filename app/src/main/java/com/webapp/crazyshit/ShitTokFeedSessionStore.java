package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Independent, in-memory feed positions for the five ShitTok preference tabs.
 * Videos and resolved URLs are retained, not decoder/player instances.
 */
final class ShitTokFeedSessionStore {
    static final int MAX_ITEMS = 160;
    private static final int MAX_STREAMS = 32;

    static final class Session {
        final String selectedUrl;
        final int selectedIndex;
        final long positionMs;
        final boolean paused;
        final List<NativeContentItem> queue;
        final Map<String, CrazyShitRepository.StreamInfo> streams;

        private Session(String selectedUrl, int selectedIndex, long positionMs, boolean paused,
                List<NativeContentItem> queue, Map<String, CrazyShitRepository.StreamInfo> streams) {
            this.selectedUrl = selectedUrl;
            this.selectedIndex = selectedIndex;
            this.positionMs = positionMs;
            this.paused = paused;
            this.queue = Collections.unmodifiableList(queue);
            this.streams = Collections.unmodifiableMap(streams);
        }
    }

    private final Map<String, Session> feeds = new HashMap<>();

    void save(ShitTokSourceMode source, ContentPreference preference,
            List<NativeContentItem> queue, int selectedIndex, long positionMs, boolean paused,
            Map<String, CrazyShitRepository.StreamInfo> streams) {
        if (source == null || preference == null || queue == null || queue.isEmpty()
                || selectedIndex < 0 || selectedIndex >= queue.size()) return;
        NativeContentItem selected = queue.get(selectedIndex);
        if (selected == null || selected.url == null || selected.url.isEmpty()) return;

        // Retain a bounded real playlist, including the selected clip and enough
        // preceding items to support swiping backwards after a category return.
        int start = Math.min(Math.max(0, selectedIndex - 40),
                Math.max(0, queue.size() - MAX_ITEMS));
        int end = Math.min(queue.size(), start + MAX_ITEMS);
        List<NativeContentItem> copy = new ArrayList<>(queue.subList(start, end));
        Map<String, CrazyShitRepository.StreamInfo> resolved = new LinkedHashMap<>();
        // Prioritize the current video; a full source cache should not evict it.
        CrazyShitRepository.StreamInfo current = streams.get(selected.url);
        if (current != null) resolved.put(selected.url, current);
        for (int i = 0; i < copy.size() && resolved.size() < MAX_STREAMS; i++) {
            NativeContentItem item = copy.get(i);
            if (item == null || item.url == null) continue;
            CrazyShitRepository.StreamInfo value = streams.get(item.url);
            if (value != null) resolved.put(item.url, value);
        }
        feeds.put(source.key(preference), new Session(selected.url, selectedIndex - start,
                Math.max(0L, positionMs), paused, copy, resolved));
    }

    Session get(ShitTokSourceMode source, ContentPreference preference) {
        return source == null || preference == null ? null : feeds.get(source.key(preference));
    }

    void clear() { feeds.clear(); }
}
