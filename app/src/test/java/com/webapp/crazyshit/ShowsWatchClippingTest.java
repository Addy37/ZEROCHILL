package com.webapp.crazyshit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ShowsWatchClippingTest {
    @Test
    public void motionOverflowIsOnlyEnabledDuringHandoff() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        LinearLayout shell = new LinearLayout(activity);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        ShowsWatchLaunch.setMotionOverflow(root, true);
        assertFalse(root.getClipChildren());
        assertFalse(root.getClipToPadding());
        assertFalse(shell.getClipChildren());
        assertFalse(shell.getClipToPadding());

        ShowsWatchLaunch.setMotionOverflow(root, false);
        assertTrue(root.getClipChildren());
        assertTrue(root.getClipToPadding());
        assertTrue(shell.getClipChildren());
        assertTrue(shell.getClipToPadding());
    }
}
