package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Stable title match ordering. Does not drop results or call the network. */
final class SearchRelevance {
    private SearchRelevance() {}

    static List<NativeContentItem> prefer(String query, List<NativeContentItem> items) {
        if (items == null || items.size() < 2) return items;
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (needle.length() < 2) return items;
        ArrayList<NativeContentItem> ordered = new ArrayList<>(items);
        ordered.sort((left, right) -> Integer.compare(score(needle, left), score(needle, right)));
        return ordered;
    }

    private static int score(String needle, NativeContentItem item) {
        String title = item == null || item.title == null ? "" : item.title.toLowerCase(Locale.US);
        if (title.equals(needle)) return 0;
        if (title.startsWith(needle)) return 1;
        if (title.contains(needle)) return 2;
        return 3;
    }
}
