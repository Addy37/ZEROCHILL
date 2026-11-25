package com.webapp.crazyshit;

/** How a provider's card should look. List mode always uses the existing row. */
enum SearchCardStyle {
    LIST,
    LANDSCAPE,
    PORTRAIT,
    COVER;

    static SearchCardStyle forScope(SearchPanel.Scope scope, SearchLayoutPreference.Mode mode) {
        if (mode != SearchLayoutPreference.Mode.GRID) return LIST;
        if (scope == null) return LANDSCAPE;
        switch (scope) {
            case ONLYFAP:
                return PORTRAIT;
            case BUNKR_ALBUMS:
            case EROME:
                return COVER;
            case SHOWS:
            case BADDIEHUB:
            case LIBRARY:
            case XMEGADRIVE:
            default:
                return LANDSCAPE;
        }
    }

    float widthOverHeight() {
        switch (this) {
            case PORTRAIT:
                return 3f / 4f;
            case COVER:
                return 1f;
            case LANDSCAPE:
            case LIST:
            default:
                return 16f / 9f;
        }
    }
}
