package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.TextView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real native components must consume the persisted palette, including white. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class AccentCoverageTest {
    @After public void clearPreferences() {
        AppearanceTheme.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit();
    }

    @Test public void everyAccentReachesNativeActivityDialogAndSharedControls() {
        Context app = RuntimeEnvironment.getApplication();
        for (AppearanceTheme.Accent accent : AppearanceTheme.Accent.values()) {
            AppearanceTheme.setAccent(app, accent);
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            activity.setTheme(R.style.Theme_CrazyShit);
            AppearanceTheme.applyRuntimeTheme(activity);
            assertAccentAttribute(activity, accent.color);

            android.widget.ProgressBar progress = new android.widget.ProgressBar(activity);
            ZeroChillUi.styleProgress(progress);
            assertEquals(accent.color, progress.getIndeterminateTintList().getDefaultColor());
            TextView action = new TextView(activity);
            ZeroChillUi.styleAction(action, true);
            assertEquals(AppearanceTheme.onAccent(activity), action.getCurrentTextColor());
            TextView chip = new TextView(activity);
            ZeroChillUi.styleChip(chip, true);
            assertEquals(accent.color, chip.getCurrentTextColor());
            ZeroChillEditText field = new ZeroChillEditText(activity);
            assertEquals((accent.color & 0xFFFFFF) | 0x33000000, field.getHighlightColor());

            ZeroChillDialog.Builder dialog = new ZeroChillDialog.Builder(activity);
            assertAccentAttribute(dialog.getContext(), accent.color);
            assertEquals(Color.BLACK, AppearanceTheme.background(activity));
            activity.finish();
        }
    }

    @Test public void returningToCyanReplacesPreviousOverlayOnSameContext() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.PURPLE);
        AppearanceTheme.applyRuntimeTheme(activity);
        assertAccentAttribute(activity, AppearanceTheme.Accent.PURPLE.color);
        AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.CYAN);
        AppearanceTheme.applyRuntimeTheme(activity);
        assertAccentAttribute(activity, AppearanceTheme.Accent.CYAN.color);
        activity.finish();
    }

    @Test public void recreatedNativeHostReadsSavedAccentAndOpticsWithoutReset() {
        Context app = RuntimeEnvironment.getApplication();
        AppearanceTheme.setAccent(app, AppearanceTheme.Accent.WHITE);
        AppearanceTheme.setGlassPreset(app, AppearanceTheme.GlassPreset.QUALITY);
        AppearanceTheme.setGlassFloat(app, AppearanceTheme.KEY_GLASS_BLUR, 2.5f);
        AppearanceTheme.setMenuTint(app, .8f);
        org.robolectric.android.controller.ActivityController<Activity> first =
                Robolectric.buildActivity(Activity.class).setup();
        first.pause().stop().destroy();
        Activity second = Robolectric.buildActivity(Activity.class).setup().get();
        second.setTheme(R.style.Theme_CrazyShit);
        AppearanceTheme.applyRuntimeTheme(second);
        assertAccentAttribute(second, Color.WHITE);
        assertEquals(AppearanceTheme.GlassPreset.CUSTOM, AppearanceTheme.glassPreset(second));
        assertEquals(2.5f, AppearanceTheme.glassBlur(second), .001f);
        assertEquals(.8f, AppearanceTheme.menuTint(second), .001f);
        assertTrue(AppearanceTheme.glassColorSplitting(second));
        assertNotEquals(Color.WHITE, AppearanceTheme.onAccent(second));
        second.finish();
    }

    private static void assertAccentAttribute(Context context, int expected) {
        TypedValue value = new TypedValue();
        assertTrue(context.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true));
        int actual = value.resourceId == 0 ? value.data : context.getColor(value.resourceId);
        assertEquals(expected, actual);
    }
}
