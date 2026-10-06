package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local user preference for hiding OnlyFap creators from ShitTok without hiding them elsewhere. */
final class ShitTokBlockedCreatorStore {
    private static final String PREFS = "shittok_blocked_creators";
    private static final String KEY_CREATORS = "creator_keys";
    private static final String LABEL_PREFIX = "label:";

    private ShitTokBlockedCreatorStore() {
    }

    static Set<String> keys(Context context) {
        if (context == null) return new HashSet<>();
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new HashSet<>(preferences.getStringSet(KEY_CREATORS, Collections.emptySet()));
    }

    static boolean isBlocked(Set<String> blockedKeys, NativeContentItem item) {
        if (blockedKeys == null || blockedKeys.isEmpty()) return false;
        String key = keyFor(item);
        return !key.isEmpty() && blockedKeys.contains(key);
    }

    static synchronized String block(Context context, NativeContentItem item) {
        if (context == null) return "";
        String name = ShitTokCreatorMetadata.creatorName(item);
        String key = keyForName(name);
        if (key.isEmpty()) return "";

        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> blocked = new HashSet<>(
                preferences.getStringSet(KEY_CREATORS, Collections.emptySet())
        );
        blocked.add(key);
        preferences.edit()
                .putStringSet(KEY_CREATORS, blocked)
                .putString(LABEL_PREFIX + key, displayName(name))
                .apply();
        return key;
    }

    static synchronized void unblock(Context context, String key) {
        if (context == null || key == null || key.trim().isEmpty()) return;
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> blocked = new HashSet<>(
                preferences.getStringSet(KEY_CREATORS, Collections.emptySet())
        );
        blocked.remove(key);
        preferences.edit()
                .putStringSet(KEY_CREATORS, blocked)
                .remove(LABEL_PREFIX + key)
                .apply();
    }

    static List<BlockedCreator> all(Context context) {
        if (context == null) return Collections.emptyList();
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ArrayList<BlockedCreator> result = new ArrayList<>();
        for (String key : keys(context)) {
            if (key == null || key.trim().isEmpty()) continue;
            String label = preferences.getString(LABEL_PREFIX + key, key);
            result.add(new BlockedCreator(key, clean(label).isEmpty() ? key : clean(label)));
        }
        result.sort(Comparator.comparing(value -> value.label.toLowerCase(java.util.Locale.US)));
        return result;
    }

    static String summary(Context context) {
        int count = keys(context).size();
        if (count == 0) return "None";
        return count == 1 ? "1 blocked" : count + " blocked";
    }

    static String keyFor(NativeContentItem item) {
        return keyForName(ShitTokCreatorMetadata.creatorName(item));
    }

    static String keyForName(String name) {
        return CreatorIdentity.key(clean(name));
    }

    private static String displayName(String name) {
        String clean = clean(name);
        return clean.isEmpty() ? clean : CreatorIdentity.display(clean);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static final class BlockedCreator {
        final String key;
        final String label;

        BlockedCreator(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }
}
