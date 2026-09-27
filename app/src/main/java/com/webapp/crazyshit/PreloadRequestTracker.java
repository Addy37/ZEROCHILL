package com.webapp.crazyshit;

import java.util.LinkedHashSet;

/** Bounded in-memory guard against repeatedly scheduling the same speculative preload. */
final class PreloadRequestTracker {
    private final int maxEntries;
    private final LinkedHashSet<String> keys = new LinkedHashSet<>();

    PreloadRequestTracker(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    synchronized boolean markIfNew(String key) {
        if (key == null || key.isEmpty() || keys.contains(key)) return false;
        keys.add(key);
        while (keys.size() > maxEntries) {
            String oldest = keys.iterator().next();
            keys.remove(oldest);
        }
        return true;
    }

    synchronized int size() {
        return keys.size();
    }
}
