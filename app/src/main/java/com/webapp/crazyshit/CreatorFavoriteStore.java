package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Persistent creator favorites used by Fapzone cards. */
final class CreatorFavoriteStore {
    private static final String PREFS = "creator_favorites";
    private static final String KEY_CREATORS = "creators";

    private CreatorFavoriteStore() {
    }

    static boolean contains(Context context, NativeContentItem creator) {
        String logical = logicalKey(context, creator);
        if (logical.isEmpty()) return false;
        for (String favorite : names(context)) {
            if (logical.equals(logicalKey(context, favorite))) return true;
        }
        return false;
    }

    static synchronized boolean toggle(Context context, NativeContentItem creator) {
        String rawKey = key(creator);
        String logical = logicalKey(context, creator);
        if (rawKey.isEmpty() || logical.isEmpty()) return false;

        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(
                preferences.getStringSet(KEY_CREATORS, new HashSet<>())
        );

        boolean favorite = true;
        boolean removed = favorites.removeIf(
                stored -> logical.equals(logicalKey(context, stored)));
        if (removed) {
            favorite = false;
        } else {
            favorites.add(rawKey);
        }

        preferences.edit().putStringSet(KEY_CREATORS, favorites).apply();
        CreatorCatalog.remember(context, java.util.Collections.singletonList(creator));
        return favorite;
    }

    static Set<String> names(Context context) {
        return new HashSet<>(context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_CREATORS, new HashSet<>()));
    }

    static String key(NativeContentItem creator) {
        if (creator == null) return "";
        String value = clean(creator.searchQuery);
        if (value.isEmpty()) value = clean(creator.title);
        return value.toLowerCase(Locale.US).replaceAll("\\s+", " ");
    }

    static String logicalKey(Context context, NativeContentItem creator) {
        return logicalKey(context, key(creator));
    }

    static String logicalKey(Context context, String value) {
        String raw = clean(value);
        if (raw.isEmpty()) return "";
        String canonical = BundledCreatorIndex.get(context.getApplicationContext())
                .canonicalKey(raw);
        String logical = CreatorNameMatcher.identity(canonical);
        return logical.isEmpty() ? CreatorNameMatcher.identity(raw) : logical;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
