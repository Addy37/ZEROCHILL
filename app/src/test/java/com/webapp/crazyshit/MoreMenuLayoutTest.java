package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class MoreMenuLayoutTest {
    @Test
    public void primaryGridAndSecondaryRowsPreserveActionsAndBadges() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = host.get();
            Dialog dialog = new Dialog(activity);
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            AtomicInteger invoked = new AtomicInteger();
            List<LandscapeMoreDialog.Action> actions = Arrays.asList(
                    action("Downloads", 0, invoked), action("Messages", 104, invoked),
                    action("Settings", 0, invoked), action("Account", 0, invoked),
                    action("Send feedback", 0, invoked), action("Support ZEROCHILL", 0, invoked));
            TextView downloadBadge = LandscapeMoreDialog.populateActions(activity, dialog, content, actions);
            assertEquals(5, content.getChildCount());
            assertEquals(View.GONE, downloadBadge.getVisibility());
            LinearLayout first = (LinearLayout) content.getChildAt(0);
            LinearLayout second = (LinearLayout) content.getChildAt(1);
            assertEquals(2, first.getChildCount());
            assertEquals(2, second.getChildCount());
            String[] titles = {"Downloads", "Messages", "Settings", "Account"};
            for (int i = 0; i < 4; i++) {
                LinearLayout tile = (LinearLayout) ((i < 2) ? first : second).getChildAt(i % 2);
                assertEquals(titles[i], ((TextView) tile.getChildAt(1)).getText().toString());
                assertEquals(1f, ((LinearLayout.LayoutParams) tile.getLayoutParams()).weight, 0f);
                assertTrue(tile.isClickable());
            }
            LinearLayout messages = (LinearLayout) first.getChildAt(1);
            TextView messageBadge = (TextView) ((FrameLayout) messages.getChildAt(0)).getChildAt(1);
            assertEquals("99+", messageBadge.getText().toString());
            assertEquals(View.VISIBLE, messageBadge.getVisibility());
            LinearLayout feedback = (LinearLayout) content.getChildAt(3);
            LinearLayout support = (LinearLayout) content.getChildAt(4);
            assertEquals("Send feedback", ((TextView) feedback.getChildAt(1)).getText().toString());
            assertEquals("Support ZEROCHILL", ((TextView) support.getChildAt(1)).getText().toString());
            dialog.setContentView(content);
            dialog.show();
            assertTrue(first.getChildAt(0).performClick());
            assertEquals(1, invoked.get());
            assertTrue(!dialog.isShowing());
        }
    }

    private static LandscapeMoreDialog.Action action(String title, int badge, AtomicInteger invoked) {
        return new LandscapeMoreDialog.Action(R.drawable.ic_action_download, title,
                "Accessible description", badge, invoked::incrementAndGet);
    }
}
