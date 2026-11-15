package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class SupportZeroChillDialogTest {
    @Test
    public void showsBothSupportChoicesAlongsideMessageAndDismissAction() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Dialog dialog = LandscapeMoreDialog.createSupportDialog(activity);
        dialog.show();

        TextView github = dialog.findViewById(LandscapeMoreDialog.SUPPORT_GITHUB_VIEW_ID);
        TextView cashApp = dialog.findViewById(LandscapeMoreDialog.SUPPORT_CASH_APP_VIEW_ID);
        assertNotNull(github);
        assertNotNull(cashApp);
        assertEquals("GitHub Sponsors", github.getText().toString());
        assertEquals("Cash App ($SupportZC)", cashApp.getText().toString());
        assertEquals(View.VISIBLE, github.getVisibility());
        assertEquals(View.VISIBLE, cashApp.getVisibility());
        assertTrue(github.isClickable());
        assertTrue(cashApp.isClickable());
        TextView later = dialog.findViewById(LandscapeMoreDialog.SUPPORT_LATER_VIEW_ID);
        assertNotNull(later);
        assertEquals("Not now", later.getText().toString());
        assertTrue(later.isClickable());
        assertEquals(7, LandscapeMoreDialog.buildSupportContent(activity).getChildCount());
    }

    @Test
    public void windowHasFinalSizeAndNoScaleAnimationAtFirstShow() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Dialog dialog = LandscapeMoreDialog.createSupportDialog(activity);
        dialog.show();

        WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
        int dp = Math.round(activity.getResources().getDisplayMetrics().density * 380);
        int margin = Math.round(activity.getResources().getDisplayMetrics().density * 32);
        assertEquals(Math.min(dp, activity.getResources().getDisplayMetrics().widthPixels - margin),
                attrs.width);
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, attrs.height);
        assertEquals(Gravity.CENTER, attrs.gravity);
        assertEquals(0, attrs.windowAnimations);
        assertEquals(1f, dialog.getWindow().getDecorView().getScaleX(), 0.001f);
        assertEquals(1f, dialog.getWindow().getDecorView().getScaleY(), 0.001f);
    }

    @Test
    @Config(qualifiers = "w320dp-h360dp-xhdpi")
    public void shortDisplayKeepsSupportChoicesReachableWithLargeText() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f);
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = host.get();
            Dialog dialog = LandscapeMoreDialog.createSupportDialog(activity);
            dialog.show();
            View decor = dialog.getWindow().getDecorView();
            int width = dialog.getWindow().getAttributes().width;
            int height = activity.getResources().getDisplayMetrics().heightPixels;
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, width, height);
            View last = dialog.findViewById(LandscapeMoreDialog.SUPPORT_LATER_VIEW_ID);
            assertTrue("Test must use enlarged text", ((TextView) last).getTextSize()
                    > 14f * activity.getResources().getDisplayMetrics().density);
            last.requestRectangleOnScreen(new android.graphics.Rect(0, 0, last.getWidth(), last.getHeight()), true);
            android.view.ViewGroup contentRoot = dialog.findViewById(android.R.id.content);
            View viewport = contentRoot.getChildAt(0);
            assertTrue("Action must have a real measured height", last.getHeight() > 0);
            assertTrue("Viewport must have a real measured height", viewport.getHeight() > 0);
            assertTrue("Viewport must fit the available display", viewport.getHeight() <= height);
            int[] actionLocation = new int[2], viewportLocation = new int[2];
            last.getLocationInWindow(actionLocation);
            viewport.getLocationInWindow(viewportLocation);
            assertTrue("Action top must be reachable: action=" + actionLocation[1]
                            + ", viewport=" + viewportLocation[1] + ", scroll=" + viewport.getScrollY(),
                    actionLocation[1] >= viewportLocation[1]);
            assertTrue("Action bottom must be reachable: action=" + actionLocation[1]
                            + "+" + last.getHeight() + ", viewport=" + viewportLocation[1]
                            + "+" + viewport.getHeight() + ", scroll=" + viewport.getScrollY(),
                    actionLocation[1] + last.getHeight() <= viewportLocation[1] + viewport.getHeight());
            last.performClick();
            assertTrue(!dialog.isShowing());
        } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f); }
    }

    @Test
    public void dismissChoiceDoesNotOpenExternalDestination() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Dialog dialog = LandscapeMoreDialog.createSupportDialog(activity);
        dialog.show();
        dialog.findViewById(LandscapeMoreDialog.SUPPORT_LATER_VIEW_ID).performClick();
        assertTrue(!dialog.isShowing());
        assertEquals(null, Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void githubChoiceOpensCorrectExternalDestination() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Dialog dialog = LandscapeMoreDialog.createSupportDialog(activity);
        dialog.show();

        dialog.findViewById(LandscapeMoreDialog.SUPPORT_GITHUB_VIEW_ID).performClick();
        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://github.com/sponsors/Addy37", intent.getDataString());
    }

    @Test
    public void cashAppChoiceOpensCorrectExternalDestination() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Dialog dialog = LandscapeMoreDialog.createSupportDialog(activity);
        dialog.show();

        dialog.findViewById(LandscapeMoreDialog.SUPPORT_CASH_APP_VIEW_ID).performClick();
        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://cash.app/$SupportZC", intent.getDataString());
    }
}
