package com.webapp.crazyshit;

import android.content.Context;

/** Remembers Grid or List separately for each Spotlight provider. */
final class SearchLayoutPreference {
    enum Mode { GRID, LIST }

    private static final String PREFS = "spotlight_layouts";

    private SearchLayoutPreference() {}

    static Mode get(Context context, SearchPanel.Scope scope) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key(scope), "");
        if ("LIST".equals(stored)) return Mode.LIST;
        if ("GRID".equals(stored)) return Mode.GRID;
        return fallback(scope);
    }

    static void set(Context context, SearchPanel.Scope scope, Mode mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key(scope), mode.name())
                .apply();
    }

    /** Shows, OnlyFap, Bunkr, Erome, BaddieHub and Library open as a grid. XMegaDrive stays a list. */
    static Mode fallback(SearchPanel.Scope scope) {
        return scope == SearchPanel.Scope.XMEGADRIVE ? Mode.LIST : Mode.GRID;
    }

    private static String key(SearchPanel.Scope scope) {
        return scope == null ? SearchPanel.Scope.SHOWS.name() : scope.name();
    }
}
