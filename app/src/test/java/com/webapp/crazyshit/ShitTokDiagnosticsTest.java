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
