package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

/** Legacy stored categories plus OnlyFap-only presentation choices. */
enum ContentPreference {
    ALL("all", "All content", "ALL"),
    WOMEN("women", "Women", "♀"),
    MEN("men", "Men", "♂"),
    TRANS_WOMEN("trans_women", "Trans women", "⚧"),
    TRANS_MEN("trans_men", "Trans men", "⚧"),
    GROUPS("groups", "Groups", ""),
    PAWCHIVE("pawchive", "Pawchive", ""),
    TRANS("trans", "Trans", "⚧");

    private static final String PAWCHIVE_SELECTED = "onlyfap_pawchive_selected";
    static ContentPreference[] standardValues() {
        return new ContentPreference[]{ALL, WOMEN, MEN, TRANS_WOMEN, TRANS_MEN, GROUPS};
    }
    static ContentPreference[] onlyFapValues() {
        return new ContentPreference[]{ALL, WOMEN, MEN, TRANS, GROUPS, PAWCHIVE};
    }
    static ContentPreference onlyFapMode(ContentPreference value) {
        return value == TRANS_WOMEN || value == TRANS_MEN ? TRANS : value;
    }
    static ContentPreference getOnlyFap(Context context) {
        return prefs(context).getBoolean(PAWCHIVE_SELECTED, false) ? PAWCHIVE : onlyFapMode(get(context));
    }
    static synchronized boolean setOnlyFap(Context context, ContentPreference value) {
        value = onlyFapMode(value);
        if (value == null || value == getOnlyFap(context)) return false;
        // Retain the pre-existing ShitTok choice even if its lazy migration has not run.
        ShitTokContentPreference.get(context);
        if (value == TRANS) {
            ContentPreference legacy = get(context);
            // Keep old stored values readable by older APKs; never persist the synthetic mode.
            return set(context, onlyFapMode(legacy) == TRANS ? legacy : TRANS_WOMEN);
        }
        if (value != PAWCHIVE) return set(context, value);
        SharedPreferences prefs = prefs(context);
        prefs.edit().putBoolean(PAWCHIVE_SELECTED, true)
                .putLong(REVISION, prefs.getLong(REVISION, 0L) + 1L).apply();
        return true;
    }
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
        for (ContentPreference option : standardValues()) if (option.value.equals(value)) return option;
        return ALL;
    }
    static synchronized boolean set(Context context, ContentPreference value) {
        if (value == null || value == PAWCHIVE || value == TRANS || (value == get(context) && getOnlyFap(context) != PAWCHIVE)) return false;
        SharedPreferences prefs = prefs(context);
        prefs.edit().putBoolean(PAWCHIVE_SELECTED, false).putString(KEY, value.value)
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
        if (this == PAWCHIVE) return R.drawable.ic_content_pawchive;
        return this == GROUPS ? R.drawable.ic_content_groups : 0;
    }
}
