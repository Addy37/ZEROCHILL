package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Local display relationships. The creator catalog and favorite keys are never rewritten. */
final class ManualCreatorMergeStore {
    private static final String PREFS = "manual_creator_merges_v1";
    private static final String KEY = "groups";
    private static List<Group> cache;

    static final class Group {
        final Set<String> keys;
        final String nameKey;
        final String avatarKey;

        Group(Set<String> keys, String nameKey, String avatarKey) {
            this.keys = Collections.unmodifiableSet(new LinkedHashSet<>(keys));
            this.nameKey = nameKey;
            this.avatarKey = avatarKey;
        }
    }

    private ManualCreatorMergeStore() { }

    static synchronized List<Group> load(Context context) {
        if (cache == null) {
            cache = new ArrayList<>();
            String raw = context.getApplicationContext().getSharedPreferences(PREFS, 0)
                    .getString(KEY, "[]");
            try {
                JSONArray groups = new JSONArray(raw);
                Set<String> used = new HashSet<>();
                for (int i = 0; i < Math.min(500, groups.length()); i++) {
                    JSONObject object = groups.optJSONObject(i);
                    if (object == null) continue;
                    JSONArray members = object.optJSONArray("keys");
                    if (members == null || members.length() < 2 || members.length() > 500) continue;
                    Set<String> keys = new LinkedHashSet<>();
                    boolean valid = true;
                    for (int j = 0; j < members.length(); j++) {
                        String key = members.optString(j, "");
                        if (key.isEmpty() || key.length() > 250 || !keys.add(key)
                                || used.contains(key)) { valid = false; break; }
                    }
                    String name = object.optString("name", "");
                    String avatar = object.optString("avatar", "");
                    if (!valid || keys.size() < 2 || !keys.contains(name)
                            || !keys.contains(avatar)) continue;
                    cache.add(new Group(keys, name, avatar));
                    used.addAll(keys);
                }
            } catch (Exception ignored) { /* Invalid local state displays unmerged favorites. */ }
        }
        return new ArrayList<>(cache);
    }

    static synchronized boolean merge(Context context, Set<String> source, Set<String> target,
                                      String nameKey, String avatarKey) {
        if (source.isEmpty() || target.isEmpty() || !Collections.disjoint(source, target)) return false;
        Set<String> combined = new LinkedHashSet<>(source);
        combined.addAll(target);
        if (combined.size() > 500 || !combined.contains(nameKey) || !combined.contains(avatarKey)) return false;
        List<Group> next = load(context);
        // Existing groups are flattened. No directed edges or cycles can be created.
        next.removeIf(group -> !Collections.disjoint(group.keys, combined));
        next.add(new Group(combined, nameKey, avatarKey));
        return save(context, next);
    }

    static synchronized boolean changePrimary(Context context, Set<String> members,
                                              String nameKey, String avatarKey) {
        if (!members.contains(nameKey) || !members.contains(avatarKey)) return false;
        List<Group> next = load(context);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).keys.equals(members)) {
                next.set(i, new Group(members, nameKey, avatarKey));
                return save(context, next);
            }
        }
        return false;
    }

    static synchronized boolean unmerge(Context context, Set<String> members) {
        List<Group> next = load(context);
        if (!next.removeIf(group -> group.keys.equals(members))) return false;
        return save(context, next);
    }

    private static boolean save(Context context, List<Group> next) {
        JSONArray groups = new JSONArray();
        try {
            for (Group group : next) {
                JSONArray keys = new JSONArray();
                for (String key : group.keys) keys.put(key);
                groups.put(new JSONObject().put("keys", keys).put("name", group.nameKey)
                        .put("avatar", group.avatarKey));
            }
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, 0);
            if (!prefs.edit().putString(KEY, groups.toString()).commit()) return false;
            cache = next;
            return true;
        } catch (Exception ignored) { return false; }
    }

    static synchronized void clearCacheForTest() { cache = null; }
}
