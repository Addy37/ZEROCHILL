package com.webapp.crazyshit;

import android.content.Context;

/** Candidate restriction only. Ranking/learning and the global gender preference stay intact. */
enum ShitTokSourceMode {
    ALL("all", "All"), ONLY_FAP("onlyfap", "OnlyFap");

    static final String KEY = "shittok_source_mode";
    final String value, label;
    ShitTokSourceMode(String value, String label) { this.value = value; this.label = label; }
    static ShitTokSourceMode get(Context context) {
        return "onlyfap".equals(ContentPreference.prefs(context).getString(KEY, "all")) ? ONLY_FAP : ALL;
    }
    static boolean set(Context context, ShitTokSourceMode mode) {
        if (mode == null || mode == get(context)) return false;
        ContentPreference.prefs(context).edit().putString(KEY, mode.value).apply();
        return true;
    }
    String key(ContentPreference gender) { return value + ":" + gender.value; }
    boolean creatorInventory(ContentPreference gender) { return this == ONLY_FAP || gender != ContentPreference.ALL; }
    boolean showEligible(ContentPreference gender) {
        return gender == ContentPreference.WOMEN || this == ONLY_FAP && gender == ContentPreference.ALL;
    }
    boolean allowsSource(String source) {
        return this == ALL || source.equals("onlyhaven") || source.equals("fapello")
                || source.equals("bunkr") || source.equals("baddiehub") || source.equals("shit-show");
    }
}
