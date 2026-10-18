package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persists a bounded history of creators recently used by the OnlyFap hero. */
final class OnlyFapHeroHistory {
    private static final String PREFS = "onlyfap_hero_history";
    static final int MAX_RECENT_CREATORS = 24;

    private OnlyFapHeroHistory() { }

    static Set<String> recent(Context context) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (context == null) return result;

        SharedPreferences prefs =
                context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<Entry> entries = entries(prefs);
        int count = Math.min(MAX_RECENT_CREATORS, entries.size());
        for (int i = 0; i < count; i++) {
            result.add(entries.get(i).key);
        }
        if (entries.size() > MAX_RECENT_CREATORS) {
            prune(prefs, entries);
        }
        return result;
    }

    static void remember(Context context, NativeContentItem creator) {
        if (context == null || creator == null) return;
        String key = CreatorFavoriteStore.key(creator);
        if (key.isEmpty()) return;

        SharedPreferences prefs =
                context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putLong(key, System.currentTimeMillis()).apply();

        List<Entry> entries = entries(prefs);
        if (entries.size() > MAX_RECENT_CREATORS) {
            prune(prefs, entries);
        }
    }

    private static List<Entry> entries(SharedPreferences prefs) {
        ArrayList<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, ?> value : prefs.getAll().entrySet()) {
            if (!(value.getValue() instanceof Long)) continue;
            entries.add(new Entry(value.getKey(), (Long) value.getValue()));
        }
        entries.sort(Comparator
                .comparingLong((Entry entry) -> entry.timestamp)
                .reversed()
                .thenComparing(entry -> entry.key));
        return entries;
    }

    private static void prune(SharedPreferences prefs, List<Entry> entries) {
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = MAX_RECENT_CREATORS; i < entries.size(); i++) {
            editor.remove(entries.get(i).key);
        }
        editor.apply();
    }

    private static final class Entry {
        final String key;
        final long timestamp;

        Entry(String key, long timestamp) {
            this.key = key;
            this.timestamp = timestamp;
        }
    }
}
