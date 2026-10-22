package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

/** One optional local preference shared by Settings, OnlyFap and ShitTok. */
enum ContentPreference {
    ALL("all", "All content", "ALL"),
    WOMEN("women", "Women", "♀"),
    MEN("men", "Men", "♂"),
    TRANS_WOMEN("trans_women", "Trans women", "⚧"),
    TRANS_MEN("trans_men", "Trans men", "⚧"),
    GROUPS("groups", "Groups", "");

    static final String KEY = "content_preference";
    private static final String REVISION = "content_preference_revision";
    final String value, label, symbol;
    ContentPreference(String value, String label, String symbol) {
        this.value = value; this.label = label; this.symbol = symbol;
    }
    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
    }
    static ContentPreference get(Context context) {
        String value = prefs(context).getString(KEY, "all");
        for (ContentPreference option : values()) if (option.value.equals(value)) return option;
        return ALL;
    }
    static synchronized boolean set(Context context, ContentPreference value) {
        if (value == null || value == get(context)) return false;
        SharedPreferences prefs = prefs(context);
        prefs.edit().putString(KEY, value.value)
                .putLong(REVISION, prefs.getLong(REVISION, 0L) + 1L).apply();
        return true;
    }
    /** Acknowledged before drawing so recreation cannot replay a presentation. */
    static synchronized boolean acknowledge(Context context, String surface) {
        SharedPreferences prefs = prefs(context);
        long revision = prefs.getLong(REVISION, 0L);
        String key = "content_preference_ack_" + surface;
        if (revision == 0 || prefs.getLong(key, 0L) == revision) return false;
        return prefs.edit().putLong(key, revision).commit();
    }
    int icon() {
        return this == GROUPS ? R.drawable.ic_content_groups : 0;
    }
}
