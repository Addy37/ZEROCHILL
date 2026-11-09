package com.webapp.crazyshit;

import android.app.Activity;
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
    static final String KEY_GLASS_PRESET = "appearance_glass_preset";
    static final String KEY_GLASS_TRANSPARENCY = "appearance_glass_transparency";
    static final String KEY_GLASS_BLUR = "appearance_glass_blur";
    static final String KEY_GLASS_REFRACTION = "appearance_glass_refraction";
    static final String KEY_GLASS_VIBRANCY = "appearance_glass_vibrancy";
    static final String KEY_GLASS_EDGE = "appearance_glass_edge_light";
    static final String KEY_GLASS_SPLIT = "appearance_glass_color_splitting";
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

    enum GlassPreset {
        OFF("Off"), FAST("Fast"), BALANCED("Balanced"), QUALITY("Quality"), CUSTOM("Custom");
        final String label;
        GlassPreset(String label) { this.label = label; }
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

    static GlassPreset glassPreset(Context context) {
        String name = prefs(context).getString(KEY_GLASS_PRESET, null);
        if (name != null) try { return GlassPreset.valueOf(name); }
        catch (IllegalArgumentException ignored) { }
        return GlassPreset.BALANCED;
    }

    static boolean hasAdvancedGlass(Context context) {
        return prefs(context).contains(KEY_GLASS_PRESET)
                || prefs(context).contains(KEY_GLASS_TRANSPARENCY)
                || prefs(context).contains(KEY_GLASS_REFRACTION);
    }

    static float glassTransparency(Context context) {
        return readFloat(context, KEY_GLASS_TRANSPARENCY, 0.78f, 0f, 1f);
    }

    static float glassBlur(Context context) {
        return readFloat(context, KEY_GLASS_BLUR, 0.7f, 0f, 12f);
    }

    static float glassRefraction(Context context) {
        return readFloat(context, KEY_GLASS_REFRACTION, 21f, 0f, 30f);
    }

    static float glassVibrancy(Context context) {
        return readFloat(context, KEY_GLASS_VIBRANCY, 1.2f, 1f, 2f);
    }

    static float glassEdge(Context context) {
        return readFloat(context, KEY_GLASS_EDGE, .70f, 0f, 1f);
    }

    static boolean glassColorSplitting(Context context) {
        return prefs(context).getBoolean(KEY_GLASS_SPLIT, false);
    }

    static void setGlassPreset(Context context, GlassPreset preset) {
        SharedPreferences.Editor edit = prefs(context).edit().putString(KEY_GLASS_PRESET, preset.name());
        switch (preset) {
            case OFF:
                edit.putFloat(KEY_GLASS_TRANSPARENCY, .85f)
                    .putFloat(KEY_GLASS_BLUR, 0f)
                    .putFloat(KEY_GLASS_REFRACTION, 0f)
                    .putFloat(KEY_GLASS_VIBRANCY, 1f)
                    .putFloat(KEY_GLASS_EDGE, 0f)
                    .putBoolean(KEY_GLASS_SPLIT, false);
                break;
            case FAST:
                edit.putFloat(KEY_GLASS_TRANSPARENCY, .65f)
                    .putFloat(KEY_GLASS_BLUR, 0f)
                    .putFloat(KEY_GLASS_REFRACTION, 0f)
                    .putFloat(KEY_GLASS_VIBRANCY, 1.1f)
                    .putFloat(KEY_GLASS_EDGE, .3f)
                    .putBoolean(KEY_GLASS_SPLIT, false);
                break;
            case BALANCED:
                edit.putFloat(KEY_GLASS_TRANSPARENCY, .78f)
                    .putFloat(KEY_GLASS_BLUR, .7f)
                    .putFloat(KEY_GLASS_REFRACTION, 21f)
                    .putFloat(KEY_GLASS_VIBRANCY, 1.2f)
                    .putFloat(KEY_GLASS_EDGE, .7f)
                    .putBoolean(KEY_GLASS_SPLIT, false);
                break;
            case QUALITY:
                edit.putFloat(KEY_GLASS_TRANSPARENCY, .88f)
                    .putFloat(KEY_GLASS_BLUR, 1.1f)
                    .putFloat(KEY_GLASS_REFRACTION, 30f)
                    .putFloat(KEY_GLASS_VIBRANCY, 1.5f)
                    .putFloat(KEY_GLASS_EDGE, 1f)
                    .putBoolean(KEY_GLASS_SPLIT, true);
                break;
            default: break;
        }
        edit.apply();
    }

    static void setGlassFloat(Context context, String key, float value) {
        float min = KEY_GLASS_VIBRANCY.equals(key) ? 1f : 0f;
        float max = KEY_GLASS_BLUR.equals(key) ? 12f
                : KEY_GLASS_REFRACTION.equals(key) ? 30f
                : KEY_GLASS_VIBRANCY.equals(key) ? 2f : 1f;
        if (!isGlassFloatKey(key)) throw new IllegalArgumentException("Unknown glass preference");
        prefs(context).edit().putFloat(key, Math.max(min, Math.min(max, Float.isNaN(value) ? min : value)))
                .putString(KEY_GLASS_PRESET, GlassPreset.CUSTOM.name()).apply();
    }

    static void setGlassColorSplitting(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_GLASS_SPLIT, enabled)
                .putString(KEY_GLASS_PRESET, GlassPreset.CUSTOM.name()).apply();
    }

    static boolean isGlassFloatKey(String key) {
        return KEY_GLASS_TRANSPARENCY.equals(key) || KEY_GLASS_BLUR.equals(key)
                || KEY_GLASS_REFRACTION.equals(key) || KEY_GLASS_VIBRANCY.equals(key)
                || KEY_GLASS_EDGE.equals(key);
    }

    static boolean validGlassFloat(String key, double value) {
        if (!isGlassFloatKey(key) || !Double.isFinite(value)) return false;
        double min = KEY_GLASS_VIBRANCY.equals(key) ? 1d : 0d;
        double max = KEY_GLASS_BLUR.equals(key) ? 12d
                : KEY_GLASS_REFRACTION.equals(key) ? 30d
                : KEY_GLASS_VIBRANCY.equals(key) ? 2d : 1d;
        return value >= min && value <= max;
    }

    private static float readFloat(Context context, String key, float fallback, float min, float max) {
        float saved = prefs(context).getFloat(key, fallback);
        return Float.isNaN(saved) ? fallback : Math.max(min, Math.min(max, saved));
    }

    static void reset(Context context) {
        setMode(context, Mode.OLED);
        prefs(context).edit()
                .remove(KEY_ACCENT)
                .remove(KEY_GLASS)
                .remove(KEY_MENU_TINT)
                .remove(KEY_GLASS_PRESET)
                .remove(KEY_GLASS_TRANSPARENCY)
                .remove(KEY_GLASS_BLUR)
                .remove(KEY_GLASS_REFRACTION)
                .remove(KEY_GLASS_VIBRANCY)
                .remove(KEY_GLASS_EDGE)
                .remove(KEY_GLASS_SPLIT)
                .apply();
        // Motion preference is intentionally left alone: it predates Appearance.
    }

    /** Values requiring new native View colors. The glass sliders update live. */
    static String colorSignature(Context context) {
        return mode(context).name() + "/" + isLight(context) + "/" + accentChoice(context).name();
    }

    /** Select native light/dark styles before UI inflation; leave fullscreen media themes alone. */
    static void applyRuntimeTheme(Activity activity) {
        boolean supported = activity instanceof NativeMainActivity
                || activity instanceof SettingsActivity
                || activity instanceof AppearanceActivity
                || activity instanceof ZeroChillSocialSettingsActivity
                || activity instanceof ZeroChillAccountActivity
                || activity instanceof ZeroChillPublicProfileActivity
                || activity instanceof ZeroChillInboxActivity
                || activity instanceof ZeroChillMessageActivity;
        if (!supported) return;
        if (isLight(activity)) {
            activity.setTheme(activity instanceof ZeroChillAccountActivity
                    ? R.style.Theme_ZeroChill_Light_Account : R.style.Theme_ZeroChill_Light);
        } else if (mode(activity) == Mode.DARK || mode(activity) == Mode.FOLLOW_SYSTEM) {
            activity.setTheme(activity instanceof ZeroChillAccountActivity
                    ? R.style.Theme_ZeroChill_Dark_Account : R.style.Theme_ZeroChill_Dark);
        }
        switch (accentChoice(activity)) {
            case PURPLE: activity.getTheme().applyStyle(R.style.ThemeOverlay_ZeroChill_Accent_Purple, true); break;
            case BLUE: activity.getTheme().applyStyle(R.style.ThemeOverlay_ZeroChill_Accent_Blue, true); break;
            case GREEN: activity.getTheme().applyStyle(R.style.ThemeOverlay_ZeroChill_Accent_Green, true); break;
            case RED: activity.getTheme().applyStyle(R.style.ThemeOverlay_ZeroChill_Accent_Red, true); break;
            case PINK: activity.getTheme().applyStyle(R.style.ThemeOverlay_ZeroChill_Accent_Pink, true); break;
            case WHITE: activity.getTheme().applyStyle(R.style.ThemeOverlay_ZeroChill_Accent_White, true); break;
            default: break;
        }
    }

    static String signature(Context context) {
        return mode(context).name() + "/" + accentChoice(context).name()
                + "/" + glassIntensity(context) + "/" + menuTint(context)
                + "/" + glassPreset(context).name() + "/" + glassTransparency(context)
                + "/" + glassBlur(context) + "/" + glassRefraction(context)
                + "/" + glassVibrancy(context) + "/" + glassEdge(context)
                + "/" + glassColorSplitting(context);
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
