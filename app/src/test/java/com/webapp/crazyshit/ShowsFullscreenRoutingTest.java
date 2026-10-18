package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;
import android.os.Looper;
import java.time.Duration;
import static org.robolectric.Shadows.shadowOf;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShowsFullscreenRoutingTest {
    private VideoDetailActivity activity() {
        // An empty launch attaches the Activity without starting a decoder or source request.
        VideoDetailActivity activity = Robolectric.buildActivity(VideoDetailActivity.class, new Intent()).create().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        for (String field : new String[] {"mediaUrl","pageUrl","title","views","uploader","comments",
                "userAgent","cookies","relatedFeedUrl","source","mediaReferer","posterUrl"}) {
            ReflectionHelpers.setField(activity, field, "");
        }
        ReflectionHelpers.setField(activity, "title", "Shows video");
        ReflectionHelpers.callInstanceMethod(activity, "buildUi");
        return activity;
    }

    private void orient(VideoDetailActivity activity, int orientation) {
        ReflectionHelpers.callInstanceMethod(activity, "applyOrientation",
                ReflectionHelpers.ClassParameter.from(int.class, orientation));
    }

    @Test public void showsManualFullscreenReusesPlayerAndReturnsEmbeddedControllerOnExit() {
        VideoDetailActivity activity = activity();
        PlayerView player = ReflectionHelpers.getField(activity, "playerView");
        Object surface = player.getVideoSurfaceView();
        PlayerControlView overlay = ReflectionHelpers.getField(activity, "fullscreenOverlay");
        View details = ReflectionHelpers.getField(activity, "detailsScroll");
        ReflectionHelpers.setField(activity, "manualLandscapeFullscreen", true);
        orient(activity, Configuration.ORIENTATION_PORTRAIT);
        assertTrue(player.getUseController());
        assertEquals(View.GONE, overlay.getVisibility());
        assertEquals(View.VISIBLE, details.getVisibility());
        ReflectionHelpers.setField(activity, "rotatableFullscreen", true);
        orient(activity, Configuration.ORIENTATION_PORTRAIT);
        assertFalse(player.getUseController());
        assertTrue(overlay.isFullyVisible());
        assertEquals(View.GONE, details.getVisibility());
        assertSame(surface, player.getVideoSurfaceView());
        orient(activity, Configuration.ORIENTATION_LANDSCAPE);
        assertSame(surface, player.getVideoSurfaceView());
        assertTrue(overlay.isFullyVisible());
        ReflectionHelpers.setField(activity, "rotatableFullscreen", false);
        orient(activity, Configuration.ORIENTATION_PORTRAIT);
        assertTrue(player.getUseController());
        assertEquals(View.GONE, overlay.getVisibility());
        assertEquals(View.VISIBLE, details.getVisibility());
        ((ShowsFullscreenControls) ReflectionHelpers.getField(activity, "fullscreenControls")).release();
    }

    @Test public void downloadedShowsAndPortraitFullscreenUseSharedChrome() {
        VideoDetailActivity activity = activity();
        PlayerView player = ReflectionHelpers.getField(activity, "playerView");
        PlayerControlView overlay = ReflectionHelpers.getField(activity, "fullscreenOverlay");
        ReflectionHelpers.setField(activity, "showsOrigin", true);
        orient(activity, Configuration.ORIENTATION_PORTRAIT);
        assertFalse(player.getUseController());
        assertTrue(overlay.isFullyVisible());
        ReflectionHelpers.setField(activity, "showsOrigin", false);
        ReflectionHelpers.setField(activity, "portraitVideo", true);
        ReflectionHelpers.setField(activity, "portraitFullscreen", true);
        orient(activity, Configuration.ORIENTATION_PORTRAIT);
        assertTrue(overlay.isFullyVisible());
        assertEquals(View.GONE, overlay.findViewById(R.id.shows_fullscreen_toggle).getVisibility());
        ReflectionHelpers.callInstanceMethod(activity, "hideVideoControls");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        ReflectionHelpers.callInstanceMethod(activity, "restoreFromSwipe");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
        assertTrue(overlay.isFullyVisible());
        assertFalse(player.isControllerFullyVisible());
        ((ShowsFullscreenControls) ReflectionHelpers.getField(activity, "fullscreenControls")).release();
    }
}
