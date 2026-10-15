package com.webapp.crazyshit;

import android.graphics.Color;

/** Shared visual palette for the ZeroChill electric-blue-on-OLED refresh. */
final class UiPalette {
    static final int PRIMARY = Color.rgb(8, 146, 208);
    static final int PRIMARY_DIM = Color.rgb(6, 122, 176);
    static final int PRIMARY_CONTAINER = Color.rgb(5, 42, 61);
    static final int ON_PRIMARY = Color.rgb(2, 15, 23);

    static final int BACKGROUND = Color.BLACK;
    static final int SURFACE = Color.rgb(16, 18, 22);
    static final int RAISED = Color.rgb(25, 28, 33);
    static final int FLOATING = Color.rgb(32, 36, 42);
    static final int TEXT_PRIMARY = Color.rgb(245, 247, 250);
    static final int TEXT_SECONDARY = Color.rgb(184, 192, 204);
    static final int TEXT_MUTED = Color.rgb(146, 157, 172);
    static final int DIVIDER = Color.rgb(41, 48, 57);
    static final int DESTRUCTIVE = Color.rgb(255, 156, 156);
    static final int WARNING = Color.rgb(230, 190, 115);
    static final int SUCCESS = Color.rgb(135, 202, 169);

    private UiPalette() {
    }
}
