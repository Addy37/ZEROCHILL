package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.List;

/** Ordered, source-agnostic grouping for large Shows collections. */
final class ShowsEpisodeRanges {
    static final int RANGE_SIZE = 25;

    private ShowsEpisodeRanges() {
    }

    static int startForIndex(int index, int total) {
        if (total <= 0) return 0;
        int safe = Math.max(0, Math.min(index, total - 1));
        return (safe / RANGE_SIZE) * RANGE_SIZE;
    }

    static String label(int start, int total) {
        if (total <= 0) return "1–0";
        int safeStart = startForIndex(start, total);
        int end = Math.min(total, safeStart + RANGE_SIZE);
        return (safeStart + 1) + "–" + end;
    }

    static List<NativeContentItem> slice(List<NativeContentItem> items, int requestedStart) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (items == null || items.isEmpty()) return result;
        int start = startForIndex(requestedStart, items.size());
        int end = Math.min(items.size(), start + RANGE_SIZE);
        result.addAll(items.subList(start, end));
        return result;
    }

    static List<Integer> starts(int total) {
        ArrayList<Integer> starts = new ArrayList<>();
        for (int start = 0; start < total; start += RANGE_SIZE) starts.add(start);
        return starts;
    }
}
