package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class AppearanceThemeTest {
    private Context app() {
        Context app = RuntimeEnvironment.getApplication();
        AppearanceTheme.prefs(app).edit().clear().commit();
        return app;
    }

    @Test public void newInstallsKeepOledAndCyan() {
        Context app = app();
        assertEquals(AppearanceTheme.Mode.OLED, AppearanceTheme.mode(app));
        assertEquals(0xFF0892D0, AppearanceTheme.accent(app));
        assertEquals(0xFF000000, AppearanceTheme.background(app));
    }

    @Test public void legacyLightPreferencesCannotChangeOledAppearance() {
        Context app = app();
        AppearanceTheme.prefs(app).edit().putBoolean("oled_black_enabled", false)
                .putString(AppearanceTheme.KEY_MODE, "LIGHT").commit();
        assertEquals(AppearanceTheme.Mode.OLED, AppearanceTheme.mode(app));
        assertTrue(AppearanceTheme.isOled(app));
        assertFalse(AppearanceTheme.isLight(app));
        assertEquals(0xFF000000, AppearanceTheme.background(app));
    }

    @Test public void themeAndAccentPersistInOriginalStore() {
        Context app = app();
        AppearanceTheme.setMode(app, AppearanceTheme.Mode.LIGHT);
        AppearanceTheme.setAccent(app, AppearanceTheme.Accent.PURPLE);
        assertEquals(AppearanceTheme.Mode.OLED, AppearanceTheme.mode(app));
        assertFalse(AppearanceTheme.isLight(app));
        assertEquals(0xFFA06CFF, AppearanceTheme.accent(app));
        assertTrue(AppearanceTheme.prefs(app).getBoolean("oled_black_enabled", true));
        assertEquals(0xFF000000, ZeroChillUi.background(app));
    }

    @Test public void sharedComponentsUseThemeColors() {
        Context app = app();
        AppearanceTheme.setMode(app, AppearanceTheme.Mode.LIGHT);
        AppearanceTheme.setAccent(app, AppearanceTheme.Accent.GREEN);
        assertEquals(AppearanceTheme.accent(app), ZeroChillUi.color(app, R.color.zc_cyan));
        assertEquals(AppearanceTheme.primaryText(app), ZeroChillUi.color(app, R.color.zc_text_primary));
        assertTrue(ZeroChillUi.glass(app) instanceof android.graphics.drawable.GradientDrawable);
        android.widget.TextView label = new android.widget.TextView(app);
        ZeroChillTypography.apply(label, ZeroChillTypography.Role.TITLE);
        assertEquals(AppearanceTheme.primaryText(app), label.getCurrentTextColor());
    }

    @Test public void sharedChromeUsesSelectedAccentWithoutChangingOledSurface() {
        Context app = app();
        AppearanceTheme.setAccent(app, AppearanceTheme.Accent.PURPLE);
        int purple = AppearanceTheme.accent(app);

        android.widget.TextView sourceChip = new android.widget.TextView(app);
        ZeroChillUi.styleSourceRailChip(sourceChip, true);
        assertEquals(purple, sourceChip.getCurrentTextColor());
        assertTrue(sourceChip.getBackground() instanceof android.graphics.drawable.GradientDrawable);
        android.graphics.drawable.GradientDrawable selected =
                (android.graphics.drawable.GradientDrawable) sourceChip.getBackground();
        assertEquals((purple & 0x00FFFFFF) | 0x33000000,
                selected.getColor().getDefaultColor());

        android.widget.ProgressBar progress = new android.widget.ProgressBar(app);
        ZeroChillUi.styleProgress(progress);
        assertEquals(purple, progress.getIndeterminateTintList().getDefaultColor());

        int[] sheen = ContentPreferenceUi.noticeSheenColors(purple);
        assertEquals(3, sheen.length);
        assertEquals(0x40, android.graphics.Color.alpha(sheen[0]));
        assertEquals(0x0A, android.graphics.Color.alpha(sheen[1]));
        assertEquals(purple & 0x00FFFFFF, sheen[1] & 0x00FFFFFF);
        assertEquals(android.graphics.Color.BLACK, ZeroChillUi.background(app));
    }

    @Test public void v2GlassOptionsPersistWithoutChangingLegacySettings() {
        Context app = app();
        AppearanceTheme.setGlassPreset(app, AppearanceTheme.GlassPreset.QUALITY);
        assertEquals(AppearanceTheme.GlassPreset.QUALITY, AppearanceTheme.glassPreset(app));
        assertTrue(AppearanceTheme.glassColorSplitting(app));
        assertEquals(30f, AppearanceTheme.glassRefraction(app), .001f);
        AppearanceTheme.setGlassFloat(app, AppearanceTheme.KEY_GLASS_BLUR, 3.5f);
        assertEquals(AppearanceTheme.GlassPreset.CUSTOM, AppearanceTheme.glassPreset(app));
        assertEquals(3.5f, AppearanceTheme.glassBlur(app), .001f);
        AppearanceTheme.setGlassColorSplitting(app, false);
        assertFalse(AppearanceTheme.glassColorSplitting(app));
        assertEquals(.5f, AppearanceTheme.glassIntensity(app), .001f);
        assertEquals(.5f, AppearanceTheme.menuTint(app), .001f);
    }

    @Test public void resetDoesNotTouchSavedMotionPreferences() {
        Context app = app();
        AppearanceTheme.prefs(app).edit().putBoolean("immersive_motion_enabled", false).commit();
        AppearanceTheme.setMode(app, AppearanceTheme.Mode.LIGHT);
        AppearanceTheme.setGlassIntensity(app, 1f);
        AppearanceTheme.setMenuTint(app, 0f);
        AppearanceTheme.reset(app);
        assertEquals(AppearanceTheme.Mode.OLED, AppearanceTheme.mode(app));
        assertEquals(0.5f, AppearanceTheme.glassIntensity(app), 0.001f);
        assertEquals(0.5f, AppearanceTheme.menuTint(app), 0.001f);
        assertFalse(AppearanceTheme.prefs(app).getBoolean("immersive_motion_enabled", true));
    }
}
