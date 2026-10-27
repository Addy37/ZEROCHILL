package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Short-lived per-Search-session cache for completed remote source results. */
final class SearchResultCache {
    private static final long TTL_MS = 60_000L;
    private static final int CAPACITY = 64;

    private static final class CacheEntry {
        final long storedAtMs;
        final List<NativeContentItem> items;

        CacheEntry(List<NativeContentItem> items) {
            storedAtMs = android.os.SystemClock.elapsedRealtime();
            this.items = new ArrayList<>(items);
        }
    }

    private final Map<String, CacheEntry> cache =
            new LinkedHashMap<String, CacheEntry>(CAPACITY, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > CAPACITY;
                }
            };

    synchronized List<NativeContentItem> get(String source, String query) {
        String key = key(source, query);
        if (key.isEmpty()) return null;
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (android.os.SystemClock.elapsedRealtime() - entry.storedAtMs >= TTL_MS) {
            cache.remove(key);
            return null;
        }
        return new ArrayList<>(entry.items);
    }

    synchronized void put(String source, String query, List<NativeContentItem> items) {
        if (items == null || items.isEmpty()) return;
        String key = key(source, query);
        if (key.isEmpty()) return;
        cache.put(key, new CacheEntry(items));
    }

    private static String key(String source, String query) {
        String safeSource = source == null ? "" : source.trim().toLowerCase(Locale.US);
        String safeQuery = query == null ? "" : query.trim().toLowerCase(Locale.US)
                .replaceAll("\\s+", " ");
        if (safeSource.isEmpty() || safeQuery.length() < 2) return "";
        return safeSource + "\n" + safeQuery;
    }
}
