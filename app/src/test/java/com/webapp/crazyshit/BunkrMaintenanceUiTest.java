package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.jsoup.Jsoup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrMaintenanceUiTest {
    private static final String PAGE = "https://bunkr.cr/f/unavailable-ui-video";

    @Test public void badgeUsesAppearanceAccentAndClearsOnReuseAndExpiry() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.PURPLE);
            FrameLayout frame = new FrameLayout(activity);
            activity.setContentView(frame);
            BunkrMaintenanceUi.Badge badge = BunkrMaintenanceUi.addBadge(frame, Gravity.TOP);
            NativeContentItem unavailable = video(PAGE);
            badge.bind(unavailable);
            assertEquals(View.GONE, badge.getVisibility());

            confirmMaintenance();
            badge.refresh();
            assertEquals(View.VISIBLE, badge.getVisibility());
            assertEquals(new PorterDuffColorFilter(AppearanceTheme.Accent.PURPLE.color, PorterDuff.Mode.SRC_ATOP),
                    badge.getColorFilter());
            assertEquals("Temporarily unavailable", badge.getContentDescription());

            badge.bind(video("https://bunkr.cr/f/healthy-ui-video"));
            assertEquals(View.GONE, badge.getVisibility());

            badge.bind(unavailable);
            assertEquals(View.VISIBLE, badge.getVisibility());
            assertTrue(badge.isAttachedToWindow());
            shadowOf(Looper.getMainLooper()).idleFor(
                    Duration.ofMillis(BunkrMaintenance.remainingMillis(PAGE) + 1));
            assertEquals(View.GONE, badge.getVisibility());
        } finally {
            BunkrMaintenance.retry(PAGE);
        }
    }

    @Test public void maintenanceDialogDistinguishesCloudflareAndClearsStateWhenRetrying() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            var previous = ShadowDialog.getLatestDialog();
            AtomicBoolean retryStarted = new AtomicBoolean();
            assertFalse(BunkrMaintenanceUi.showIfConfirmed(activity, PAGE,
                    new IOException("HTTP 403 Cloudflare verification required"), () -> { }));
            assertSame(previous, ShadowDialog.getLatestDialog());

            confirmMaintenance();
            assertTrue(BunkrMaintenanceUi.showIfConfirmed(activity, PAGE, null, () -> {
                assertFalse(BunkrMaintenance.isKnown(PAGE));
                retryStarted.set(true);
            }));
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            TextView message = dialog.findViewById(android.R.id.message);
            assertEquals("Bunkr is performing server maintenance. This video cannot play right now.",
                    message.getText().toString());
            assertEquals("Retry", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(retryStarted.get());
        } finally {
            BunkrMaintenance.retry(PAGE);
        }
    }

    private static void confirmMaintenance() {
        assertTrue(BunkrMaintenance.recordFilePage(PAGE, Jsoup.parse(
                "<p>The server hosting this file is temporarily unavailable for maintenance.</p>"
                        + "<p>Playback and downloads are disabled while maintenance is in progress.</p>", PAGE)));
    }

    private static NativeContentItem video(String url) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video", url, "", "", "", "");
    }
}
