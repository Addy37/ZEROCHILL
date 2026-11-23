package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * ShitTok-only category selection. Migrates the legacy shared preference once
 * without altering OnlyFap or Settings, which continue to use ContentPreference.
 */
final class ShitTokContentPreference {
    static final String KEY = "shittok_content_preference";

    private ShitTokContentPreference() { }

    static synchronized ContentPreference get(Context context) {
        SharedPreferences prefs = ContentPreference.prefs(context);
        String value = prefs.getString(KEY, null);
        if (value == null) {
            ContentPreference previous = ContentPreference.get(context);
            // Complete this small migration before returning, so overlapping feed
            // and settings reads cannot copy different legacy values.
            prefs.edit().putString(KEY, previous.value).commit();
            return previous;
        }
        for (ContentPreference option : ContentPreference.values()) {
            if (option.value.equals(value)) return option;
        }
        return ContentPreference.ALL;
    }

    static synchronized boolean set(Context context, ContentPreference mode) {
        if (mode == null || mode == get(context)) return false;
        ContentPreference.prefs(context).edit().putString(KEY, mode.value).apply();
        return true;
    }
}
