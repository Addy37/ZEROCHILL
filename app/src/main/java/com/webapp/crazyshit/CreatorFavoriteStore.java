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
        String key = key(creator);
        if (key.isEmpty()) return false;
        Set<String> stored = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_CREATORS, java.util.Collections.emptySet());
        return stored.contains(key) || CreatorIdentity.hasStoredAlias(stored, key);
    }

    static synchronized boolean toggle(Context context, NativeContentItem creator) {
        String key = key(creator);
        if (key.isEmpty()) return false;

        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(
                preferences.getStringSet(KEY_CREATORS, new HashSet<>())
        );
        boolean favorite;
        if (favorites.contains(key) || CreatorIdentity.hasStoredAlias(favorites, key)) {
            favorites.removeIf(saved -> saved.equals(key)
                    || CreatorIdentity.sameReviewedIdentity(saved, key));
            favorite = false;
        } else {
            favorites.add(key);
            favorite = true;
        }
        preferences.edit().putStringSet(KEY_CREATORS, favorites).apply();
        CreatorCatalog.remember(context, java.util.Collections.singletonList(creator));
        ZeroChillAccountRepository.setCreatorFavorite(
                context,
                key,
                creator.title,
                favorite
        );
        return favorite;
    }

    static Set<String> names(Context context) {
        return new HashSet<>(context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_CREATORS, new HashSet<>()));
    }

    static synchronized void mergeNames(Context context, Set<String> remote) {
        if (remote == null || remote.isEmpty()) return;
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> merged = new HashSet<>(
                preferences.getStringSet(KEY_CREATORS, new HashSet<>())
        );
        merged.addAll(remote);
        preferences.edit().putStringSet(KEY_CREATORS, merged).apply();
    }

    static String key(NativeContentItem creator) {
        if (creator == null) return "";
        String value = clean(creator.searchQuery);
        if (value.isEmpty()) value = clean(creator.title);
        return value.toLowerCase(Locale.US).replaceAll("\\s+", " ");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
