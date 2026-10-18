package com.webapp.crazyshit;

import android.content.Context;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Device-local discovery exclusions. Separate keys leave legacy history/backups untouched. */
final class ShitTokSeenStore {
    static final int SEEN_LIMIT = 8192, LIKED_LIMIT = 32768;
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor();
    private static ShitTokSeenStore shared;
    static synchronized ShitTokSeenStore get(Context context) {
        if (shared == null) shared = new ShitTokSeenStore(context.getApplicationContext());
        return shared;
    }
    private final Context app;
    private final LinkedHashSet<String> seen = new LinkedHashSet<>(), liked = new LinkedHashSet<>();
    private boolean dirty;
    ShitTokSeenStore(Context app) {
        this.app = app;
        read("seen_v1", seen, SEEN_LIMIT);
        read("liked_v1", liked, LIKED_LIMIT);
        // Migrate the old 500 URLs without deleting or rewriting the old key.
        try {
            JSONArray old = new JSONArray(app.getSharedPreferences("chaos_feed", 0).getString("recent_urls", "[]"));
            for (int i = old.length() - 1; i >= 0; i--) add(seen, ShitTokContentIdentity.page(old.optString(i)), SEEN_LIMIT);
        } catch (Exception ignored) {}
    }
    synchronized boolean seen(String page, String media) {
        return seen.contains(ShitTokContentIdentity.page(page)) || seen.contains(ShitTokContentIdentity.media(page))
                || (media != null && seen.contains(ShitTokContentIdentity.media(media)));
    }
    synchronized boolean liked(String page, String media) {
        return liked.contains("key:" + ZeroChillSocialRepository.contentKey(page))
                || liked.contains(ShitTokContentIdentity.media(page))
                || liked.contains(ShitTokContentIdentity.page(page))
                || (media != null && liked.contains(ShitTokContentIdentity.media(media)));
    }
    synchronized void watch(String page, String media) {
        add(seen, ShitTokContentIdentity.page(page), SEEN_LIMIT);
        if (media != null) add(seen, ShitTokContentIdentity.media(media), SEEN_LIMIT);
        dirty = true;
    }
    synchronized void heart(String page, String media) {
        add(liked, "key:" + ZeroChillSocialRepository.contentKey(page), LIKED_LIMIT);
        add(liked, ShitTokContentIdentity.page(page), LIKED_LIMIT);
        if (media != null) add(liked, ShitTokContentIdentity.media(media), LIKED_LIMIT);
        dirty = true;
        flush();
    }
    synchronized void importLikes(Collection<String> keys) {
        if (keys.isEmpty()) return;
        for (String key : keys) if (key != null && key.matches("[0-9a-f]{64}")) add(liked, "key:" + key, LIKED_LIMIT);
        dirty = true;
        flush();
    }
    synchronized int seenSize() { return seen.size(); }
    synchronized int likedSize() { return liked.size(); }
    synchronized void flush() {
        if (!dirty) return;
        dirty = false;
        ArrayList<String> seenCopy = new ArrayList<>(seen), likedCopy = new ArrayList<>(liked);
        // Submit while holding the lock so older snapshots cannot overwrite newer ones.
        DISK.execute(() -> app.getSharedPreferences("shittok_discovery", 0).edit()
                .putString("seen_v1", new JSONArray(seenCopy).toString())
                .putString("liked_v1", new JSONArray(likedCopy).toString()).commit());
    }
    private void read(String key, Set<String> target, int limit) {
        try {
            JSONArray values = new JSONArray(app.getSharedPreferences("shittok_discovery", 0).getString(key, "[]"));
            for (int i = Math.max(0, values.length() - limit); i < values.length(); i++) {
                String value = values.optString(i);
                if (!value.isEmpty() && value.length() <= 2048) target.add(value);
            }
        } catch (Exception ignored) {}
    }
    private static void add(LinkedHashSet<String> set, String value, int limit) {
        if (value == null || value.isEmpty() || value.length() > 2048) return;
        set.remove(value); set.add(value);
        while (set.size() > limit) set.remove(set.iterator().next());
    }
}
