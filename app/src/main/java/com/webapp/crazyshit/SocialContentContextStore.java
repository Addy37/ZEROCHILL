package com.webapp.crazyshit;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded local metadata captured from native content already shown when comments open. */
final class SocialContentContextStore {
    static final String PREFS = "zerochill_social_content_context_v1";
    private static final int LIMIT = 100;
    private SocialContentContextStore() { }

    static synchronized void remember(Context context, NativeContentItem item) {
        if (context == null || item == null || item.url.isEmpty()) return;
        try {
            JSONArray existing = read(context);
            NativeContentItem previous = find(context, item.url);
            item = OnlyFapVideoMetadata.merge(item, previous);
            JSONArray updated = new JSONArray().put(ContentItemCodec.encode(item));
            String key = ZeroChillSocialRepository.contentKey(item.url);
            for (int i = 0; i < existing.length() && updated.length() < LIMIT; i++) {
                JSONObject value = existing.optJSONObject(i);
                if (value != null && !key.equals(ZeroChillSocialRepository.contentKey(value.optString("url")))) updated.put(value);
            }
            context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("items", updated.toString()).apply();
        } catch (Exception ignored) { }
    }

    static synchronized NativeContentItem find(Context context, String pageUrl) {
        String key = ZeroChillSocialRepository.contentKey(pageUrl);
        if (key.isEmpty()) return null;
        JSONArray items = read(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject value = items.optJSONObject(i);
            if (value != null && key.equals(ZeroChillSocialRepository.contentKey(value.optString("url")))) return ContentItemCodec.decode(value);
        }
        return null;
    }

    /** Metadata-only cache of server-confirmed account likes. Never an authority for toggling. */
    static final class LikedPage {
        final java.util.List<NativeContentItem> items;
        final int unavailable;
        final int nextOffset;
        final boolean hasMore;
        LikedPage(java.util.List<NativeContentItem> items, int unavailable, int nextOffset, boolean hasMore) {
            this.items = items; this.unavailable = unavailable;
            this.nextOffset = nextOffset; this.hasMore = hasMore;
        }
    }

    static synchronized void confirmLike(Context context, String owner, String pageUrl, boolean liked) {
        if (owner.isEmpty() || !owner.equals(ZeroChillSessionStore.currentUserId(context))) return;
        String key = ZeroChillSocialRepository.contentKey(pageUrl);
        JSONArray rows = likeRows(context, owner);
        JSONArray updated = new JSONArray();
        try {
            JSONObject found = null;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                if (key.equals(row.optString("key"))) found = row;
            }
            if (liked) {
                JSONObject row = found != null ? found : new JSONObject().put("key", key)
                        .put("url", pageUrl).put("created", java.time.Instant.now().toString());
                NativeContentItem item = knownVideos(context, owner).get(key);
                if (item != null) row.put("item", ContentItemCodec.encode(item));
                updated.put(row);
            }
            for (int i = 0; i < rows.length() && updated.length() < 2000; i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row != null && !key.equals(row.optString("key"))) updated.put(row);
            }
            saveLikes(context, owner, updated);
        } catch (Exception ignored) { }
    }

    /** Called on a worker, with the account captured before the existing likes request. */
    static synchronized LikedPage cacheLikedPage(Context context, String owner, JSONArray serverRows, int offset) {
        if (!owner.equals(ZeroChillSessionStore.currentUserId(context)) || owner.isEmpty())
            throw new IllegalStateException("Account changed while loading likes.");
        JSONArray rows = offset == 0 ? new JSONArray() : likeRows(context, owner);
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (int i = 0; i < rows.length(); i++) keys.add(rows.optJSONObject(i).optString("key"));
        java.util.Map<String, NativeContentItem> known = knownVideos(context, owner);
        java.util.List<NativeContentItem> page = new java.util.ArrayList<>();
        int unavailable = 0;
        for (int i = 0; i < serverRows.length(); i++) {
            JSONObject source = serverRows.optJSONObject(i);
            if (source == null) continue;
            String key = source.optString("content_key");
            NativeContentItem item = known.get(key);
            if (item != null) page.add(item); else unavailable++;
            try {
                JSONObject row = new JSONObject().put("key", key).put("created", source.optString("created_at"));
                if (item != null) row.put("url", item.url).put("item", ContentItemCodec.encode(item));
                if (!keys.contains(key) && rows.length() < 2000) { rows.put(row); keys.add(key); }
            } catch (Exception ignored) { }
        }
        saveLikes(context, owner, rows);
        return new LikedPage(page, unavailable, offset + serverRows.length(), serverRows.length() == 100);
    }

    static synchronized java.util.List<NativeContentItem> likedVideos(Context context, String owner) {
        java.util.List<NativeContentItem> out = new java.util.ArrayList<>();
        if (owner.isEmpty() || !owner.equals(ZeroChillSessionStore.currentUserId(context))) return out;
        java.util.Map<String, NativeContentItem> known = knownVideos(context, owner);
        JSONArray rows = likeRows(context, owner);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            NativeContentItem item = row == null ? null : known.get(row.optString("key"));
            if (item != null) out.add(item);
        }
        return out;
    }

    static java.util.Map<String, NativeContentItem> knownVideos(Context context, String owner) {
        java.util.Map<String, NativeContentItem> known = new java.util.LinkedHashMap<>();
        // Keep reversible URLs from confirmed likes even after local history is trimmed.
        JSONArray likes = likeRows(context, owner);
        for (int i = 0; i < likes.length(); i++) {
            JSONObject row = likes.optJSONObject(i);
            String url = row == null ? "" : row.optString("url");
            if (!url.isEmpty()) known.put(row.optString("key"), row.optJSONObject("item") != null
                    ? ContentItemCodec.decode(row.optJSONObject("item")) : new NativeContentItem(
                    NativeContentItem.KIND_MEDIA, "Liked video", url, "", "", "", ""));
        }
        for (FavoriteStore.Item item : FavoriteStore.load(context)) {
            String key = ZeroChillSocialRepository.contentKey(item.url);
            NativeContentItem base = item.content == null
                    ? OnlyFapVideoMetadata.basic(item.title, item.url, "") : item.content;
            known.put(key, OnlyFapVideoMetadata.merge(base, known.get(key)));
        }
        JSONArray metadata = read(context);
        for (int i = 0; i < metadata.length(); i++) {
            JSONObject row = metadata.optJSONObject(i);
            NativeContentItem item = row == null ? null : ContentItemCodec.decode(row);
            if (item != null && item.isVideo()) known.put(ZeroChillSocialRepository.contentKey(item.url),
                    OnlyFapVideoMetadata.merge(item, known.get(ZeroChillSocialRepository.contentKey(item.url))));
        }
        for (PlaybackHistoryStore.Item item : PlaybackHistoryStore.load(context)) {
            String key = ZeroChillSocialRepository.contentKey(item.pageUrl);
            NativeContentItem base = item.content == null
                    ? OnlyFapVideoMetadata.basic(item.title, item.pageUrl, item.posterUrl) : item.content;
            known.put(key, OnlyFapVideoMetadata.merge(base, known.get(key)));
        }
        return known;
    }

    private static JSONArray likeRows(Context context, String owner) {
        try { return new JSONArray(context.getSharedPreferences(PREFS, 0).getString("likes:" + owner, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private static void saveLikes(Context context, String owner, JSONArray rows) {
        java.util.List<JSONObject> ordered = new java.util.ArrayList<>();
        for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) ordered.add(rows.optJSONObject(i));
        ordered.sort((a, b) -> b.optString("created").compareTo(a.optString("created")));
        JSONArray sorted = new JSONArray();
        for (JSONObject row : ordered) sorted.put(row);
        if (owner.equals(ZeroChillSessionStore.currentUserId(context))) context.getSharedPreferences(PREFS, 0)
                .edit().putString("likes:" + owner, sorted.toString()).apply();
    }

    private static JSONArray read(Context context) {
        try { return new JSONArray(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
}
