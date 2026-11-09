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

    @Test public void legacyNonOledChoiceIsPreserved() {
        Context app = app();
        AppearanceTheme.prefs(app).edit().putBoolean("oled_black_enabled", false).commit();
        assertEquals(AppearanceTheme.Mode.DARK, AppearanceTheme.mode(app));
        assertFalse(AppearanceTheme.isOled(app));
    }

    @Test public void themeAndAccentPersistInOriginalStore() {
        Context app = app();
        AppearanceTheme.setMode(app, AppearanceTheme.Mode.LIGHT);
        AppearanceTheme.setAccent(app, AppearanceTheme.Accent.PURPLE);
        assertEquals(AppearanceTheme.Mode.LIGHT, AppearanceTheme.mode(app));
        assertTrue(AppearanceTheme.isLight(app));
        assertEquals(0xFFA06CFF, AppearanceTheme.accent(app));
        assertFalse(AppearanceTheme.prefs(app).getBoolean("oled_black_enabled", true));
        assertEquals(0xFFF7F9FC, ZeroChillUi.background(app));
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
