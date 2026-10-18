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

    /** Switches only account favorites, preserving the original signed-out local collection. */
    static synchronized Set<String> activateAccount(Context context, String userId) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String active = prefs.getString("account_owner", "");
        Set<String> current = names(context);
        SharedPreferences.Editor edit = prefs.edit();
        if (!userId.equals(active)) {
            if (active.isEmpty()) edit.putStringSet("guest_creators", current);
            else edit.putStringSet("account_creators_" + active, current);
            Set<String> scoped = new HashSet<>(prefs.getStringSet("account_creators_" + userId, new HashSet<>()));
            edit.putStringSet(KEY_CREATORS, scoped).putString("account_owner", userId);
        }
        // Legacy local favorites are imported once, into the first account used after this upgrade.
        String importer = prefs.getString("legacy_import_owner", "");
        if (importer.isEmpty()) {
            importer = userId;
            edit.putString("legacy_import_owner", userId).putStringSet("legacy_import_pending", current);
        }
        edit.apply();
        return userId.equals(importer) ? new HashSet<>(prefs.getStringSet("legacy_import_pending", new HashSet<>())) : new HashSet<>();
    }

    static synchronized void replaceAccountNames(Context context, String userId, Set<String> names) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!userId.equals(prefs.getString("account_owner", ""))) return;
        SharedPreferences.Editor edit = prefs.edit().putStringSet(KEY_CREATORS, new HashSet<>(names))
                .putStringSet("account_creators_" + userId, new HashSet<>(names));
        if (userId.equals(prefs.getString("legacy_import_owner", ""))) edit.remove("legacy_import_pending");
        edit.apply();
    }

    static synchronized void deactivateAccount(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String active = prefs.getString("account_owner", "");
        if (active.isEmpty()) return;
        prefs.edit().putStringSet("account_creators_" + active, names(context))
                .putStringSet(KEY_CREATORS, new HashSet<>(prefs.getStringSet("guest_creators", new HashSet<>())))
                .remove("account_owner").apply();
    }

    static synchronized void removeAccount(Context context, String userId) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (userId.equals(prefs.getString("account_owner", ""))) deactivateAccount(context);
        SharedPreferences.Editor edit = prefs.edit().remove("account_creators_" + userId);
        if (userId.equals(prefs.getString("legacy_import_owner", ""))) edit.remove("legacy_import_pending");
        edit.apply();
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

