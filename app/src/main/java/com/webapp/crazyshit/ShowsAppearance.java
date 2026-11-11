package com.webapp.crazyshit;

import android.content.Context;

/** Shows uses the host app's appearance; no separate settings or persistence. */
final class ShowsAppearance {
    private ShowsAppearance() { }

    static android.graphics.drawable.Drawable pagerBackground(Context context) {
        String preset = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("appearance_glass_preset", "BALANCED");
        return "OFF".equals(preset)
                ? new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)
                : ZeroChillUi.panelGlass(context);
    }

    static int accent(Context context) {
        // Same app_prefs values used by the newer OG Appearance screen. Reading
        // them preserves a saved choice without introducing another preference store.
        String saved = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("appearance_accent", "CYAN");
        if (saved == null) return UiPalette.PRIMARY;
        switch (saved) {
            case "PURPLE": return 0xFFA06CFF;
            case "BLUE": return 0xFF4A89FF;
            case "GREEN": return 0xFF42D39B;
            case "RED": return 0xFFFF5B65;
            case "PINK": return 0xFFFF69B6;
            case "WHITE": return 0xFFFFFFFF;
            default: return UiPalette.PRIMARY;
        }
    }
}
