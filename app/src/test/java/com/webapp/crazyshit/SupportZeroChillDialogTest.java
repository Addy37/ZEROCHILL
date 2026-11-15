package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.app.Application;
import android.content.Intent;
import android.view.View;
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
