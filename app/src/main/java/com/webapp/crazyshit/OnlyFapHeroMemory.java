package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** One previously validated hero's metadata. Glide owns all image bytes. */
final class OnlyFapHeroMemory {
    private static final String PREFS = "onlyfap_hero_startup";
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    static final class Entry {
        final NativeContentItem creator;
        final String imageUrl;
        final String referer;
        Entry(NativeContentItem creator, String imageUrl, String referer) {
            this.creator = creator;
            this.imageUrl = imageUrl;
            this.referer = referer;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Entry read(Context context) {
        try {
            String value = prefs(context).getString("hero", "");
            if (value == null || value.isEmpty() || value.length() > 8192) return null;
            JSONObject json = new JSONObject(value);
            long age = System.currentTimeMillis() - json.optLong("saved", 0);
            if (age < 0 || age > MAX_AGE_MS) return null;
            String title = json.optString("title"), url = json.optString("url");
            String image = json.optString("image"), referer = json.optString("referer");
            if (title.isEmpty() || url.isEmpty() || !isRemoteImage(image)) return null;
            NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    title, url, json.optString("thumbnail"), "", "", "",
                    json.optString("description"), json.optString("query"));
            return new Entry(creator, image, referer);
        } catch (Exception ignored) { return null; }
    }

    static void remember(Context context, NativeContentItem creator, String image, String referer) {
        if (creator == null || !creator.isCreator() || !isRemoteImage(image)) return;
        try {
            String value = new JSONObject().put("saved", System.currentTimeMillis())
                    .put("title", creator.title).put("url", creator.url)
                    .put("thumbnail", creator.imageUrl).put("description", creator.description)
                    .put("query", creator.searchQuery).put("image", image)
                    .put("referer", referer).toString();
            if (value.length() <= 8192) prefs(context).edit().putString("hero", value).apply();
        } catch (Exception ignored) { /* Metadata is optional. */ }
    }

    static void forget(Context context, String failedImage) {
        Entry current = read(context);
        if (current != null && current.imageUrl.equals(failedImage)) {
            prefs(context).edit().remove("hero").apply();
        }
    }

    static boolean isRemoteImage(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }
}
