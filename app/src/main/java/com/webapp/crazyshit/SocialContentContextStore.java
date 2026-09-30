package com.webapp.crazyshit;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded local metadata captured from native content already shown when comments open. */
final class SocialContentContextStore {
    private static final String PREFS = "zerochill_social_content_context_v1";
    private static final int LIMIT = 100;
    private SocialContentContextStore() { }

    static synchronized void remember(Context context, NativeContentItem item) {
        if (context == null || item == null || item.url.isEmpty()) return;
        try {
            JSONArray existing = read(context);
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

    private static JSONArray read(Context context) {
        try { return new JSONArray(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
}
