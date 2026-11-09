package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * App-owned appearance state. Shares the existing app_prefs store so upgrades
 * retain settings and backups can keep their existing compatibility behavior.
 */
final class AppearanceTheme {
    static final String KEY_MODE = "appearance_theme_mode";
    static final String KEY_ACCENT = "appearance_accent";
    static final String KEY_GLASS = "appearance_glass_intensity";
    static final String KEY_MENU_TINT = "appearance_menu_tint";
    private static final String PREFS = "app_prefs";
    private static final int CYAN = 0xFF0892D0;

    enum Mode {
        FOLLOW_SYSTEM("Follow System"),
        LIGHT("Light"),
        DARK("Dark"),
        OLED("OLED Black");

        final String label;
        Mode(String label) { this.label = label; }
    }

    enum Accent {
        CYAN("Cyan", 0xFF0892D0),
        PURPLE("Purple", 0xFFA06CFF),
        BLUE("Blue", 0xFF4A89FF),
        GREEN("Green", 0xFF42D39B),
        RED("Red", 0xFFFF5B65),
        PINK("Pink", 0xFFFF69B6),
        WHITE("White", 0xFFFFFFFF);

        final String label;
        final int color;
        Accent(String label, int color) { this.label = label; this.color = color; }
    }

    private AppearanceTheme() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Mode mode(Context context) {
        String saved = prefs(context).getString(KEY_MODE, null);
        if (saved != null) {
            try { return Mode.valueOf(saved); }
            catch (IllegalArgumentException ignored) { /* Older builds may use a different value. */ }
        }
        // Never override a pre-existing OLED preference during migration.
        return prefs(context).getBoolean("oled_black_enabled", true) ? Mode.OLED : Mode.DARK;
    }

    static void setMode(Context context, Mode mode) {
        prefs(context).edit()
                .putString(KEY_MODE, mode.name())
                // Legacy native viewers still inspect this flag. Keep it in step.
                .putBoolean("oled_black_enabled", mode == Mode.OLED)
                .apply();
    }

    static Accent accentChoice(Context context) {
        String saved = prefs(context).getString(KEY_ACCENT, null);
        if (saved != null) {
            try { return Accent.valueOf(saved); }
            catch (IllegalArgumentException ignored) { }
        }
        return Accent.CYAN;
    }

    static void setAccent(Context context, Accent accent) {
        prefs(context).edit().putString(KEY_ACCENT, accent.name()).apply();
    }

    static int accent(Context context) { return accentChoice(context).color; }

    static boolean isLight(Context context) {
        switch (mode(context)) {
            case LIGHT: return true;
            case FOLLOW_SYSTEM:
                return (context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
            default: return false;
        }
    }

    static boolean isOled(Context context) { return mode(context) == Mode.OLED; }

    static float glassIntensity(Context context) {
        return clamp(prefs(context).getFloat(KEY_GLASS, 0.50f));
    }

    static float menuTint(Context context) {
        return clamp(prefs(context).getFloat(KEY_MENU_TINT, 0.50f));
    }

    static void setGlassIntensity(Context context, float value) {
        prefs(context).edit().putFloat(KEY_GLASS, clamp(value)).apply();
    }

    static void setMenuTint(Context context, float value) {
        prefs(context).edit().putFloat(KEY_MENU_TINT, clamp(value)).apply();
    }

    static void reset(Context context) {
        setMode(context, Mode.OLED);
        prefs(context).edit()
                .remove(KEY_ACCENT)
                .remove(KEY_GLASS)
                .remove(KEY_MENU_TINT)
                .apply();
        // Motion preference is intentionally left alone: it predates Appearance.
    }

    static String signature(Context context) {
        return mode(context).name() + "/" + accentChoice(context).name()
                + "/" + glassIntensity(context) + "/" + menuTint(context);
    }

    static int background(Context context) {
        if (isLight(context)) return 0xFFF7F9FC;
        return isOled(context) ? Color.BLACK : 0xFF0D1117;
    }

    static int surface(Context context) {
        return isLight(context) ? Color.WHITE : isOled(context) ? 0xFF101216 : 0xFF171B21;
    }

    static int raised(Context context) {
        return isLight(context) ? 0xFFF0F3F8 : 0xFF1B2027;
    }

    static int floating(Context context) {
        return isLight(context) ? 0xFFE7EDF5 : 0xFF242A32;
    }

    static int primaryText(Context context) {
        return isLight(context) ? 0xFF15202E : 0xFFF5F7FA;
    }

    static int secondaryText(Context context) {
        return isLight(context) ? 0xFF4F5C6C : 0xFFB8C0CC;
    }

    static int mutedText(Context context) {
        return isLight(context) ? 0xFF697989 : 0xFF929DAC;
    }

    static int outline(Context context) {
        return isLight(context) ? 0xFFD5DEE9 : 0xFF293039;
    }

    static int onAccent(Context context) {
        return accentChoice(context) == Accent.WHITE ? 0xFF15202E : 0xFF020F17;
    }

    /** Only shared semantic resources are overridden in this first stage. */
    static Integer colorOverride(Context context, int resource) {
        if (resource == R.color.zc_background || resource == R.color.app_background) return background(context);
        if (resource == R.color.zc_surface || resource == R.color.app_surface
                || resource == R.color.zc_surface_glass) return surface(context);
        if (resource == R.color.zc_surface_raised || resource == R.color.zc_surface_glass_strong
                || resource == R.color.app_surface_variant) return raised(context);
        if (resource == R.color.zc_surface_floating) return floating(context);
        if (resource == R.color.zc_text_primary || resource == R.color.app_on_surface) return primaryText(context);
        if (resource == R.color.zc_text_secondary || resource == R.color.app_on_surface_variant) return secondaryText(context);
        if (resource == R.color.zc_text_muted || resource == R.color.app_on_surface_muted) return mutedText(context);
        if (resource == R.color.zc_edge || resource == R.color.zc_divider) return outline(context);
        if (resource == R.color.zc_cyan || resource == R.color.app_primary) return accent(context);
        if (resource == R.color.zc_cyan_dim) return (accent(context) & 0x00FFFFFF) | 0x99000000;
        if (resource == R.color.app_primary_dim) return accent(context);
        if (resource == R.color.zc_surface_pressed) return raised(context);
        if (resource == R.color.zc_cyan_container || resource == R.color.app_primary_container)
            return (accent(context) & 0x00FFFFFF) | 0x33000000;
        if (resource == R.color.app_on_primary) return onAccent(context);
        if (resource == R.color.zc_disabled) return mutedText(context);
        if (resource == R.color.zc_ripple) return isLight(context) ? 0x24000000 : 0x24FFFFFF;
        return null;
    }

    private static float clamp(float value) {
        return Float.isNaN(value) ? 0.5f : Math.max(0f, Math.min(1f, value));
    }
}
