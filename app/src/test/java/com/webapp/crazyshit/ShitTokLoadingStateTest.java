package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi")
public class ShitTokLoadingStateTest {
    static void sync(Object holder) { ReflectionHelpers.callInstanceMethod(holder, "syncLoading"); }
    static int visibility(Object holder) { return ((View)ReflectionHelpers.getField(holder, "loading")).getVisibility(); }
    @Test public void onlySelectedHolderOwnsDotsAndReadyClearsThem() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            f.selectedPlayer.state = Player.STATE_BUFFERING;
            f.offscreenPlayer.state = Player.STATE_BUFFERING;
            sync(f.selected); sync(f.offscreen); sync(f.duplicate);
            assertEquals(View.VISIBLE, visibility(f.selected));
            assertEquals(View.GONE, visibility(f.offscreen));
            assertEquals(View.GONE, visibility(f.duplicate));
            ProgressBar dots = ReflectionHelpers.getField(f.selected, "loading");
            assertTrue(dots.getIndeterminateDrawable() instanceof ZeroChillDotsDrawable);
            PlayerView view = ReflectionHelpers.getField(f.selected, "playerView");
            assertEquals(PlayerView.SHOW_BUFFERING_NEVER, (int)ReflectionHelpers.getField(view, "showBuffering"));
            f.selectedPlayer.state = Player.STATE_READY; sync(f.selected);
            assertEquals(View.GONE, visibility(f.selected));
            f.selectedPlayer.state = Player.STATE_BUFFERING; sync(f.selected);
            ReflectionHelpers.setField(f.feed, "active", false); sync(f.selected);
            assertEquals(View.GONE, visibility(f.selected));
        }
    }
    @Test public void staleBufferingAndGenerationCallbacksCannotReplaceReadyState() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            Player.Listener listener = ReflectionHelpers.getField(f.selected, "uiPlayerListener");
            f.selectedPlayer.state = Player.STATE_BUFFERING;
            listener.onPlaybackStateChanged(Player.STATE_BUFFERING);
            f.selectedPlayer.state = Player.STATE_READY;
            listener.onPlaybackStateChanged(Player.STATE_READY);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.GONE, visibility(f.selected));
            ReflectionHelpers.setField(f.feed, "feedGeneration", 99);
            f.selectedPlayer.state = Player.STATE_BUFFERING;
            listener.onPlaybackStateChanged(Player.STATE_BUFFERING);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.GONE, visibility(f.selected));
        }
    }
    @Test public void legacyLaunchExtraCannotHoldShellForPlayerReadiness() {
        try (org.robolectric.android.controller.ActivityController<Activity> host =
                Robolectric.buildActivity(Activity.class).setup()) {
            host.get().setContentView(new android.widget.FrameLayout(host.get()));
            host.get().setIntent(new Intent().putExtra(ChaosStartupOverlayController.EXTRA_STARTUP_HANDOFF, true));
            ChaosStartupHandoff.begin();
            android.view.ViewGroup content = host.get().findViewById(android.R.id.content);
            int count = content.getChildCount();
            ChaosStartupOverlayController.attach(host.get());
            assertEquals(count, content.getChildCount());
            assertFalse(ChaosStartupHandoff.isWaiting());
        }
    }
}
