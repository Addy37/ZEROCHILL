package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w411dp-h891dp-xhdpi")
public class OnlyFapPreferencePickerTest {
    private Activity host;
    private FrameLayout content;
    private TextView anchor;
    @Before public void setup() {
        host = Robolectric.buildActivity(Activity.class).setup().get();
        ContentPreference.prefs(host).edit().clear().commit();
        content = new FrameLayout(host);
        anchor = new TextView(host);
        content.addView(anchor, new FrameLayout.LayoutParams(144, 144));
        host.setContentView(content);
        content.measure(View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2673, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, 1233, 2673);
    }
    @After public void close() {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog != null) dialog.dismiss();
        host.finish();
    }
    @Test public void gridKeepsAllOptionsAndPersistsOnlyActualChanges() {
        int[] changes = {0};
        ContentPreferenceUi.chooseAnchored(anchor, () -> changes[0]++);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        View root = dialog.getWindow().getDecorView();
        GridLayout grid = root.findViewWithTag("onlyfap_preference_grid");
        assertNotNull(grid);
        assertEquals(2, grid.getColumnCount());
        assertEquals(7, grid.getChildCount());
        assertNotNull(root.findViewWithTag("preference_pawchive"));
        assertTrue(root.findViewWithTag("preference_all").isSelected());
        root.findViewWithTag("preference_trans_men").performClick();
        assertEquals(ContentPreference.TRANS_MEN, ContentPreference.get(host));
        assertEquals(1, changes[0]);
        ContentPreferenceUi.chooseAnchored(anchor, () -> changes[0]++);
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.getWindow().getDecorView().findViewWithTag("preference_trans_men").isSelected());
        dialog.getWindow().getDecorView().findViewWithTag("preference_trans_men").performClick();
        assertEquals(1, changes[0]);
    }
    @Test public void pawchivePersistsWithoutChangingLegacyOrShitTokChoice() {
        ContentPreference.set(host, ContentPreference.MEN);
        ShitTokContentPreference.set(host, ContentPreference.WOMEN);
        int[] changes = {0};
        ContentPreferenceUi.chooseAnchored(anchor, () -> changes[0]++);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getWindow().getDecorView().findViewWithTag("preference_pawchive").performClick();
        assertEquals(ContentPreference.PAWCHIVE, ContentPreference.getOnlyFap(host));
        assertEquals(ContentPreference.MEN, ContentPreference.get(host));
        assertEquals(ContentPreference.WOMEN, ShitTokContentPreference.get(host));
        assertEquals(1, changes[0]);
        ContentPreferenceUi.chooseAnchored(anchor, () -> changes[0]++);
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.getWindow().getDecorView().findViewWithTag("preference_pawchive").isSelected());
        dialog.getWindow().getDecorView().findViewWithTag("preference_men").performClick();
        assertEquals(ContentPreference.MEN, ContentPreference.getOnlyFap(host));
        assertEquals(2, changes[0]);
        assertEquals(ContentPreference.WOMEN, ShitTokContentPreference.get(host));
    }
    @Test public void pawchiveUsesATintableIconInTheActiveAppearance() {
        AppearanceTheme.setAccent(host, AppearanceTheme.Accent.PINK);
        ContentPreference.setOnlyFap(host, ContentPreference.PAWCHIVE);
        ContentPreferenceUi.bindSelector(anchor);
        assertEquals(R.drawable.ic_content_pawchive, ContentPreference.PAWCHIVE.icon());
        assertNotNull(anchor.getCompoundDrawablesRelative()[0]);
        assertEquals("▾", anchor.getText().toString());

        ContentPreferenceUi.chooseAnchored(anchor, null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        android.widget.LinearLayout cell = dialog.getWindow().getDecorView().findViewWithTag("preference_pawchive");
        android.widget.ImageView icon = (android.widget.ImageView) cell.getChildAt(0);
        assertTrue(icon.getColorFilter() instanceof android.graphics.PorterDuffColorFilter);
        assertEquals(AppearanceTheme.Accent.PINK.color,
                org.robolectric.Shadows.shadowOf((android.graphics.PorterDuffColorFilter) icon.getColorFilter()).getColor());
    }

    @Test public void rapidTapIsIgnoredAndDetachClosesTheOwnedWindow() {
        ContentPreferenceUi.chooseAnchored(anchor, null);
        AlertDialog first = ShadowAlertDialog.getLatestAlertDialog();
        ContentPreferenceUi.chooseAnchored(anchor, null);
        assertSame(first, ShadowAlertDialog.getLatestAlertDialog());
        content.removeView(anchor);
        assertFalse(first.isShowing());
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNull(anchor.getTag(R.id.content_preference_picker));
    }
    @Test @Config(qualifiers = "w320dp-h240dp-xhdpi")
    public void smallLandscapeWindowIsBoundedAndReducedMotionDisablesAnimations() {
        ContentPreference.prefs(host).edit().putBoolean("immersive_motion_enabled", false).commit();
        ContentPreferenceUi.chooseAnchored(anchor, null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(0, dialog.getWindow().getAttributes().windowAnimations);
        assertTrue(dialog.getWindow().getAttributes().width <= host.getResources().getDisplayMetrics().widthPixels);
        for (ContentPreference option : ContentPreference.values())
            assertNotNull(dialog.getWindow().getDecorView().findViewWithTag("preference_" + option.value));
        dialog.getWindow().getDecorView().findViewWithTag("preference_cancel").performClick();
        assertEquals(ContentPreference.ALL, ContentPreference.get(host));
    }
    @Test @Config(sdk = {35, 36})
    public void panelStaysCenteredWhenOpenedFromOppositeScreenEdges() {
        for (int left : new int[]{0, 900}) {
            anchor.layout(left, 500, left + 144, 644);
            ContentPreferenceUi.chooseAnchored(anchor, null);
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            android.view.WindowManager.LayoutParams placement = dialog.getWindow().getAttributes();
            assertEquals(android.view.Gravity.CENTER, placement.gravity);
            assertEquals(0, placement.x);
            assertEquals(0, placement.y);
            assertTrue(placement.width <= host.getResources().getDisplayMetrics().widthPixels);
            dialog.getWindow().getDecorView().findViewWithTag("preference_cancel").performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(ContentPreference.ALL, ContentPreference.get(host));
        }
    }

    @Test @Config(sdk = {35, 36}, qualifiers = "w320dp-h240dp-xhdpi")
    public void landscapeCenteredPanelKeepsEveryOptionReachableWithoutChangingSelection() {
        ContentPreference.set(host, ContentPreference.WOMEN);
        ContentPreferenceUi.chooseAnchored(anchor, null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(android.view.Gravity.CENTER, dialog.getWindow().getAttributes().gravity);
        View root = dialog.getWindow().getDecorView();
        GridLayout grid = root.findViewWithTag("onlyfap_preference_grid");
        assertEquals(7, grid.getChildCount());
        assertNotNull(root.findViewWithTag("preference_pawchive"));
        assertTrue(root.findViewWithTag("preference_women").isSelected());
        assertTrue(grid.getParent() instanceof android.widget.ScrollView);
        root.findViewWithTag("preference_cancel").performClick();
        assertEquals(ContentPreference.WOMEN, ContentPreference.get(host));
    }

}
