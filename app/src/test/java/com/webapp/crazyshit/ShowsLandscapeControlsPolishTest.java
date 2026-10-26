package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.ui.PlayerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w800dp-h360dp-land-mdpi")
public class ShowsLandscapeControlsPolishTest {
    private Activity host() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        return activity;
    }

    @Test public void landscapeActionRailHidesCommentsAndShare() {
        Activity host = host();
        LinearLayout actions = new LinearLayout(host);
        actions.addView(action(host, "Like this video"));
        actions.addView(action(host, "Open comments"));
        actions.addView(action(host, "Save to Watch Later"));
        actions.addView(action(host, "Share video"));

        ShowsLandscapeMotion.hideSecondaryActions(actions);

        assertEquals(4, actions.getChildCount());
        assertEquals(View.VISIBLE, actions.getChildAt(0).getVisibility());
        assertEquals(View.GONE, actions.getChildAt(1).getVisibility());
        assertEquals(View.VISIBLE, actions.getChildAt(2).getVisibility());
        assertEquals(View.GONE, actions.getChildAt(3).getVisibility());
        host.finish();
    }

    @Test public void landscapeFullscreenToggleGetsExtraEndGutter() {
        Activity host = host();
        PlayerView playerView = (PlayerView) host.getLayoutInflater().inflate(
                R.layout.view_shows_fullscreen_player, null);
        host.setContentView(playerView);
        ShowsFullscreenControls controls = new ShowsFullscreenControls(playerView);
        LinearLayout progress = playerView.findViewById(R.id.shows_progress_row);
        View toggle = playerView.findViewById(R.id.shows_fullscreen_toggle);
        int baseEndPadding = progress.getPaddingEnd();

        controls.setShowsLandscapeMode(true);

        assertEquals(baseEndPadding + 20, progress.getPaddingEnd());
        assertSame(progress, toggle.getParent());

        controls.setShowsLandscapeMode(false);

        assertEquals(baseEndPadding, progress.getPaddingEnd());
        controls.release();
        host.finish();
    }

    private View action(Activity host, String description) {
        TextView view = new TextView(host);
        view.setContentDescription(description);
        return view;
    }
}
