package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;
import static com.webapp.crazyshit.ShitTokInteractionUiTest.idle;
import static com.webapp.crazyshit.ShitTokInteractionUiTest.root;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w740dp-h360dp-land-xhdpi")
public class ShitTokProgressTest {
    @Test public void timesFollowPlayingPositionAndResyncWhenControlsReopen() {
        try (ShitTokAudioOwnershipTest.Fixture fixture = ShitTokInteractionUiTest.fixture()) {
            fixture.host.get().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("immersive_motion_enabled", false).commit();
            fixture.selectedPlayer.seekAvailable = true;
            fixture.selectedPlayer.positionMs = 3_000L;
            fixture.selectedPlayer.durationMs = 28_000L;
            fixture.feed.onConfigurationChanged();
            idle(100);
            ReflectionHelpers.callInstanceMethod(fixture.selected, "showControlsTemporarily");
            View holder = root(fixture.selected);
            View overlay = holder.findViewWithTag("shittok_fullscreen_controls");
            View surface = holder.findViewWithTag("shittok_fullscreen_surface");
            TextView elapsed = holder.findViewWithTag("shittok_elapsed");
            TextView duration = holder.findViewWithTag("shittok_duration");
            SeekBar bar = ReflectionHelpers.getField(fixture.selected, "seekBar");

            assertEquals("0:03", elapsed.getText().toString());
            assertEquals("0:28", duration.getText().toString());
            fixture.selectedPlayer.positionMs = 4_000L;
            idle(250);
            assertEquals("0:04", elapsed.getText().toString());

            assertTrue(surface.performClick());
            assertEquals(View.INVISIBLE, overlay.getVisibility());
            fixture.selectedPlayer.positionMs = 14_000L;
            idle(500);
            assertEquals("0:04", elapsed.getText().toString());
            assertTrue(surface.performClick());
            assertEquals("0:14", elapsed.getText().toString());
            assertEquals(500, bar.getProgress());

            fixture.selectedPlayer.durationMs = androidx.media3.common.C.TIME_UNSET;
            ReflectionHelpers.callInstanceMethod(fixture.selected, "updateProgress");
            assertFalse(bar.isEnabled());
            assertEquals("–:––", duration.getText().toString());
        }
    }

    @Test public void timeFormattingDoesNotWrapLongClips() {
        assertEquals("0:00", ChaosFeedView.formatPlaybackTime(-1L));
        assertEquals("1:05", ChaosFeedView.formatPlaybackTime(65000L));
        assertEquals("60:03", ChaosFeedView.formatPlaybackTime(3603000L));
    }
}
