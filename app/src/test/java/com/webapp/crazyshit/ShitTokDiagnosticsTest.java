package com.webapp.crazyshit;

import android.app.Application;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokDiagnosticsTest {
    @Test public void settingsCopiesRetainedInventoryAndStartupEvidenceWithoutContentUrls() {
        try (org.robolectric.android.controller.ActivityController<SettingsActivity> host =
                Robolectric.buildActivity(SettingsActivity.class).setup()) {
            ShitTokPoolDiagnostics.snapshot("usable=2 desired=10 tailHits=3 selectedState=3 https://private.test/video?token=secret");
            ReflectionHelpers.callInstanceMethod(host.get(), "showPerformanceDetails");
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("Copy diagnostics", dialog.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            ClipboardManager clipboard = (ClipboardManager) host.get().getSystemService(Context.CLIPBOARD_SERVICE);
            String copied = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
            assertTrue(copied.contains("tailHits=3"));
            assertTrue(copied.contains("This app session"));
            assertTrue(copied.contains("Source:"));
            assertFalse(copied.contains("private.test"));
            assertFalse(copied.contains("secret"));
        }
    }
    @Test public void openingSettingsRetainsLastActiveFeedSnapshot() {
        try (ShitTokContinuityTest.Fixture f = new ShitTokContinuityTest.Fixture()) {
            f.feed.onHostPause();
            String snapshot = ShitTokPoolDiagnostics.userReport();
            assertTrue(snapshot.contains("reason=hostPause"));
            assertTrue(snapshot.contains("selectedState=2"));
            assertTrue(snapshot.contains("recentFallback=0"));
        }
    }
}
