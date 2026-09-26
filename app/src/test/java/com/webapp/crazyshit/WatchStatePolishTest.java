package com.webapp.crazyshit;

import android.app.Application;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class WatchStatePolishTest {

    @Test
    public void sectionHeadingIsNotTreatedAsContinueBadge() {
        LinearLayout parent = new LinearLayout(RuntimeEnvironment.getApplication());
        TextView heading = new TextView(parent.getContext());
        heading.setText("Continue Watching");
        parent.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        assertFalse(WatchStatePolish.isWatchStateBadge(heading));
    }

    @Test
    public void mediaOverlayAtTopStartIsTreatedAsWatchStateBadge() {
        FrameLayout parent = new FrameLayout(RuntimeEnvironment.getApplication());
        TextView badge = new TextView(parent.getContext());
        badge.setText("Continue  1:23");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        parent.addView(badge, params);

        assertTrue(WatchStatePolish.isWatchStateBadge(badge));
    }

    @Test
    public void unrelatedFrameTextIsNotTreatedAsWatchStateBadge() {
        FrameLayout parent = new FrameLayout(RuntimeEnvironment.getApplication());
        TextView text = new TextView(parent.getContext());
        text.setText("Continue Watching");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.START;
        parent.addView(text, params);

        assertFalse(WatchStatePolish.isWatchStateBadge(text));
    }
}
