package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w411dp-h891dp-xhdpi")
public class PreferenceGlassPanelTest {
    private Activity host;
    @Before public void setup() {
        host = Robolectric.buildActivity(Activity.class).setup().get();
        ContentPreference.prefs(host).edit().clear().commit();
    }
    @After public void close() {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog != null) dialog.dismiss();
        host.finish();
    }
    @Test public void compactPanelHasTransparentUnselectedRowsAndSharpNativeControls() {
        ContentPreference.set(host, ContentPreference.GROUPS);
        ContentPreferenceUi.choose(host, null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        View root = dialog.getWindow().getDecorView();
        ViewGroup glass = root.findViewWithTag("preference_glass_panel");
        assertNotNull(glass);
        assertTrue(ContentPreferenceUi.pickerWidth(host) <= BrowseUi.dp(host, 292));
        assertTrue(ContentPreferenceUi.pickerWidth(host) < host.getResources().getDisplayMetrics().widthPixels * .81f);
        assertNull(root.findViewWithTag("preference_men").getBackground());
        assertNotNull(root.findViewWithTag("preference_groups").getBackground());
        assertTrue(root.findViewWithTag("preference_groups").isSelected());
        assertTrue(glass.getChildAt(0) instanceof LinearLayout);
        assertTrue(root.findViewWithTag("preference_men").getMinimumHeight() >= BrowseUi.dp(host, 48));
        root.findViewWithTag("preference_men").performClick();
        assertEquals(ContentPreference.MEN, ContentPreference.get(host));
    }
    @Test public void formationSettlesWithoutARepeatingAnimator() {
        ContentPreferenceUi.choose(host, null);
        ViewGroup panel = ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView()
                .findViewWithTag("preference_glass_panel");
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertEquals(1f, (float) ReflectionHelpers.getField(panel, "formation"), .01f);
        assertEquals(1f, panel.getChildAt(0).getAlpha(), .01f);
    }
    @Test public void reducedMotionShowsCompleteShapeAndLabelsImmediately() {
        ContentPreference.prefs(host).edit().putBoolean("immersive_motion_enabled", false).commit();
        ContentPreferenceUi.choose(host, null);
        ViewGroup panel = ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView()
                .findViewWithTag("preference_glass_panel");
        assertEquals(1f, (float) ReflectionHelpers.getField(panel, "formation"), 0f);
        assertEquals(1f, panel.getChildAt(0).getAlpha(), 0f);
        assertEquals(0, ShadowAlertDialog.getLatestAlertDialog().getWindow().getAttributes().windowAnimations);
    }
    @Test @Config(qualifiers = "w320dp-h480dp-xhdpi")
    public void smallWindowKeepsMarginsAndAllOptionsReachable() {
        assertTrue(ContentPreferenceUi.pickerWidth(host) <= host.getResources().getDisplayMetrics().widthPixels * .81f);
        ContentPreferenceUi.choose(host, null);
        View root = ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();
        for (ContentPreference mode : ContentPreference.values())
            assertNotNull(root.findViewWithTag("preference_" + mode.value));
        assertNotNull(root.findViewWithTag("preference_cancel"));
    }
}
