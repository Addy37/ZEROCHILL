package com.webapp.crazyshit;

import android.graphics.Color;
import android.widget.ImageView;

/** How a provider's card should look. List mode always uses the existing row. */
enum SearchCardStyle {
    LIST,
    LANDSCAPE,
    PORTRAIT,
    COVER,
    /** Real image ratio, filled, with the thumbnail rounded on every corner. */
    NATURAL;

    static SearchCardStyle forScope(SearchPanel.Scope scope, SearchLayoutPreference.Mode mode) {
        if (mode != SearchLayoutPreference.Mode.GRID) return LIST;
        if (scope == null) return LANDSCAPE;
        switch (scope) {
            case ONLYFAP:
            case BUNKR_ALBUMS:
            case EROME:
            case XMEGADRIVE:
            case BADDIEHUB:
                return NATURAL;
            case SHOWS:
            case LIBRARY:
            default:
                return LANDSCAPE;
        }
    }

    boolean naturalAspect() {
        return this == NATURAL;
    }

    float widthOverHeight() {
        switch (this) {
            case PORTRAIT:
                return 3f / 4f;
            case COVER:
            case NATURAL:
                return 1f;
            case LANDSCAPE:
            case LIST:
            default:
                return 16f / 9f;
        }
    }

    /** Grid thumbnails fill the tile. List rows are unchanged. */
    ImageView.ScaleType thumbnailScale() {
        return this == COVER ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP;
    }

    static void clipThumbnail(ImageView image, int radiusDp) {
        float density = image.getResources().getDisplayMetrics().density;
        image.setBackground(BrowseUi.rounded(
                image.getContext(),
                Color.rgb(20, 20, 23),
                Math.round(radiusDp * density)
        ));
        image.setClipToOutline(true);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }
}
