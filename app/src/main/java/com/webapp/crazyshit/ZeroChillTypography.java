package com.webapp.crazyshit;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

/** Shared readable hierarchy; brand wordmarks keep their own artwork and spans. */
final class ZeroChillTypography {
    enum Role {
        DISPLAY(30, true, UiPalette.TEXT_PRIMARY), TITLE(24, true, UiPalette.TEXT_PRIMARY),
        SECTION(20, true, UiPalette.TEXT_PRIMARY), CONTENT(16, true, UiPalette.TEXT_PRIMARY),
        BODY(14, false, UiPalette.TEXT_PRIMARY), SUPPORT(13, false, UiPalette.TEXT_SECONDARY),
        META(12, false, UiPalette.TEXT_MUTED), MICRO(11, false, UiPalette.TEXT_MUTED);
        final float size;
        final boolean bold;
        final int color;
        Role(float size, boolean bold, int color) { this.size = size; this.bold = bold; this.color = color; }
    }
    private ZeroChillTypography() {}
    static void apply(TextView view, Role role) {
        if (view == null) return;
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, role.size);
        view.setTextColor(role.color);
        view.setTypeface(Typeface.create(role.bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setLetterSpacing(role == Role.DISPLAY || role == Role.TITLE ? -0.015f : 0f);
        view.setLineSpacing(0f, role == Role.BODY || role == Role.SUPPORT ? 1.12f : 1f);
    }
}
