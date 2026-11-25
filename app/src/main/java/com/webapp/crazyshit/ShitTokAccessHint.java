package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

/** A few quiet chevron nudges, then silence after the swipe has been used. */
final class ShitTokAccessHint {
    static final int APPEARANCE_LIMIT = 3;
    private static final String PREFS = "shittok_access_hint";
    private static final String LEARNED = "learned";
    private static final String SEEN = "seen";
    private static final String LAST = "last";

    private ShitTokAccessHint() {}

    /** Counts a new eligible clip. False once the swipe is learned or the first few are done. */
    static boolean noteAppearance(Context context, String clipKey) {
        if (context == null || clipKey == null || clipKey.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(LEARNED, false)) return false;
        if (clipKey.equals(prefs.getString(LAST, ""))) return false;
        int seen = prefs.getInt(SEEN, 0);
        if (seen >= APPEARANCE_LIMIT) return false;
        prefs.edit().putInt(SEEN, seen + 1).putString(LAST, clipKey).apply();
        return true;
    }

    static void learned(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(LEARNED, true).apply();
    }
}
