package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class FavoriteStore {
    private static final String PREFS = "watch_later";
    private static final String KEY_ITEMS = "items";

    private FavoriteStore() {
    }

    public static List<Item> load(Context context) {
        List<Item> items = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String url = object.optString("url", "");
                if (url.isEmpty()) continue;
                items.add(new Item(
                        object.optString("title", "Saved video"),
                        url,
                        object.optLong("savedAt", 0L),
                        OnlyFapVideoMetadata.decode(object.optJSONObject("content"), url)
                ));
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public static void add(Context context, String title, String url) {
        add(context, OnlyFapVideoMetadata.basic(title, url, ""));
    }

    public static synchronized void add(Context context, NativeContentItem content) {
        if (content == null || content.url.trim().isEmpty()) return;
        String title = content.title, url = content.url;
        List<Item> items = load(context);
        NativeContentItem saved = null;
        for (Item item : items) if (item.url.equals(url)) saved = item.content;
        content = OnlyFapVideoMetadata.merge(content, saved);
        items.removeIf(item -> item.url.equals(url));
        items.add(0, new Item(
                title == null || title.trim().isEmpty() ? "Saved video" : title.trim(),
                url,
                System.currentTimeMillis(),
                OnlyFapVideoMetadata.applies(content) ? content : null
        ));
        save(context, items);
    }

    public static synchronized void remove(Context context, String url) {
        List<Item> items = load(context);
        items.removeIf(item -> item.url.equals(url));
        save(context, items);
    }

    static synchronized void enrich(Context context, NativeContentItem content) {
        List<Item> items = load(context);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (!item.url.equals(content.url)) continue;
            NativeContentItem merged = OnlyFapVideoMetadata.merge(content, item.content);
            items.set(i, new Item(item.title, item.url, item.savedAt, merged));
            save(context, items);
            return;
        }
    }

    public static boolean contains(Context context, String url) {
        if (url == null) return false;
        for (Item item : load(context)) {
            if (url.equals(item.url)) return true;
        }
        return false;
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ITEMS)
                .apply();
    }

    private static void save(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject object = new JSONObject();
                object.put("title", item.title);
                object.put("url", item.url);
                object.put("savedAt", item.savedAt);
                if (item.content != null) object.put("content", ContentItemCodec.encode(item.content));
                array.put(object);
            }
        } catch (Exception ignored) {
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    public static final class Item {
        public final String title;
        public final String url;
        public final long savedAt;
        public final NativeContentItem content;

        public Item(String title, String url, long savedAt) {
            this(title, url, savedAt, null);
        }

        Item(String title, String url, long savedAt, NativeContentItem content) {
            this.content = content;
            this.title = title;
            this.url = url;
            this.savedAt = savedAt;
        }
    }
}
