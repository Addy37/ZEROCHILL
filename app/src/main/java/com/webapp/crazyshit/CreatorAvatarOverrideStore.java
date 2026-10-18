package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local display-only avatar overrides for Favorite Creator groups. */
final class CreatorAvatarOverrideStore {
    private static final String PREFS = "creator_avatar_overrides_v1";
    private static final String KEY = "avatars";
    private static final int MAX_GROUPS = 500;
    private static final int MAX_KEYS = 500;
    static final float DEFAULT_FOCUS = 0.5f;
    static final float DEFAULT_ZOOM = 1f;
    static final float MAX_ZOOM = 5f;
    private static List<Override> cache;

    static final class Override {
        final Set<String> keys;
        final String imageUrl;
        final String referer;
        final float focusX;
        final float focusY;
        final float zoom;

        Override(
                Set<String> keys,
                String imageUrl,
                String referer,
                float focusX,
                float focusY,
                float zoom
        ) {
            this.keys = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(keys));
            this.imageUrl = imageUrl;
            this.referer = referer;
            this.focusX = clamp(focusX, 0f, 1f);
            this.focusY = clamp(focusY, 0f, 1f);
            this.zoom = clamp(zoom, DEFAULT_ZOOM, MAX_ZOOM);
        }
    }

    private CreatorAvatarOverrideStore() { }

    static synchronized List<Override> load(Context context) {
        if (cache == null) {
            cache = new ArrayList<>();
            String raw = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]");
            try {
                JSONArray values = new JSONArray(raw);
                for (int i = 0; i < Math.min(MAX_GROUPS, values.length()); i++) {
                    JSONObject object = values.optJSONObject(i);
                    if (object == null) continue;
                    JSONArray storedKeys = object.optJSONArray("keys");
                    if (storedKeys == null || storedKeys.length() < 1
                            || storedKeys.length() > MAX_KEYS) continue;
                    LinkedHashSet<String> keys = new LinkedHashSet<>();
                    boolean valid = true;
                    for (int j = 0; j < storedKeys.length(); j++) {
                        String value = storedKeys.optString(j, "");
                        if (value.isEmpty() || value.length() > 250 || !keys.add(value)) {
                            valid = false;
                            break;
                        }
                    }
                    String imageUrl = object.optString("image", "").trim();
                    String referer = object.optString("referer", "").trim();
                    float focusX = (float) object.optDouble("focusX", DEFAULT_FOCUS);
                    float focusY = (float) object.optDouble("focusY", DEFAULT_FOCUS);
                    float zoom = (float) object.optDouble("zoom", DEFAULT_ZOOM);
                    if (!valid || keys.isEmpty() || !validRemoteUrl(imageUrl)
                            || (!referer.isEmpty() && !validRemoteUrl(referer))
                            || !validCrop(focusX, focusY, zoom)) continue;
                    cache.add(new Override(keys, imageUrl, referer, focusX, focusY, zoom));
                }
            } catch (Exception ignored) {
                // Invalid local state simply falls back to the creator's normal avatar.
            }
        }
        return new ArrayList<>(cache);
    }

    static Override find(List<Override> values, Set<String> keys) {
        if (values == null || keys == null || keys.isEmpty()) return null;
        for (Override value : values) {
            if (value.keys.equals(keys)) return value;
        }
        return null;
    }

    static synchronized boolean save(
            Context context,
            Set<String> keys,
            String imageUrl,
            String referer
    ) {
        return save(
                context,
                keys,
                imageUrl,
                referer,
                DEFAULT_FOCUS,
                DEFAULT_FOCUS,
                DEFAULT_ZOOM
        );
    }

    static synchronized boolean save(
            Context context,
            Set<String> keys,
            String imageUrl,
            String referer,
            float focusX,
            float focusY,
            float zoom
    ) {
        if (!validKeys(keys) || !validRemoteUrl(imageUrl)
                || (referer != null && !referer.trim().isEmpty() && !validRemoteUrl(referer))
                || !validCrop(focusX, focusY, zoom)) {
            return false;
        }
        List<Override> next = load(context);
        next.removeIf(value -> value.keys.equals(keys));
        if (next.size() >= MAX_GROUPS) next.remove(0);
        next.add(new Override(
                keys,
                imageUrl.trim(),
                referer == null ? "" : referer.trim(),
                focusX,
                focusY,
                zoom
        ));
        return persist(context, next);
    }

    static synchronized boolean clear(Context context, Set<String> keys) {
        if (!validKeys(keys)) return false;
        List<Override> next = load(context);
        if (!next.removeIf(value -> value.keys.equals(keys))) return false;
        return persist(context, next);
    }

    static boolean has(Context context, Set<String> keys) {
        return find(load(context), keys) != null;
    }

    private static boolean persist(Context context, List<Override> next) {
        try {
            JSONArray values = new JSONArray();
            for (Override value : next) {
                JSONArray keys = new JSONArray();
                for (String key : value.keys) keys.put(key);
                values.put(new JSONObject()
                        .put("keys", keys)
                        .put("image", value.imageUrl)
                        .put("referer", value.referer)
                        .put("focusX", value.focusX)
                        .put("focusY", value.focusY)
                        .put("zoom", value.zoom));
            }
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!prefs.edit().putString(KEY, values.toString()).commit()) return false;
            cache = new ArrayList<>(next);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean validKeys(Set<String> keys) {
        if (keys == null || keys.isEmpty() || keys.size() > MAX_KEYS) return false;
        for (String key : keys) {
            if (key == null || key.isEmpty() || key.length() > 250) return false;
        }
        return true;
    }

    private static boolean validCrop(float focusX, float focusY, float zoom) {
        return Float.isFinite(focusX)
                && Float.isFinite(focusY)
                && Float.isFinite(zoom)
                && focusX >= 0f
                && focusX <= 1f
                && focusY >= 0f
                && focusY <= 1f
                && zoom >= DEFAULT_ZOOM
                && zoom <= MAX_ZOOM;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean validRemoteUrl(String value) {
        if (value == null) return false;
        String clean = value.trim();
        if (clean.length() < 8 || clean.length() > 4096) return false;
        String lower = clean.toLowerCase(Locale.US);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    static synchronized void clearCacheForTest() {
        cache = null;
    }
}
