package com.webapp.crazyshit;

import android.app.Application;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi")
public class ShitTokFocusModeViewportTest {
    @Test public void focusModeReclaimsViewportAndRestoresItOnBack() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            View root = ((RecyclerView.ViewHolder) f.selected).itemView;
            PlayerView playerView = ReflectionHelpers.getField(f.selected, "playerView");
            ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(f.feed, "playbackOwner");
            ShitTokDynamicPinchController pinch = ReflectionHelpers.getField(f.selected, "pinch");

            int originalBottomPadding = root.getPaddingBottom();
            int originalPlayerHeight = playerView.getLayoutParams().height;
            String ownerBefore = owner.summary();

            ShitTokDynamicPinchTest.begin(root);
            ShitTokDynamicPinchTest.grow(root);
            ShitTokDynamicPinchTest.release(root, 200, 600);
            ShitTokDynamicPinchTest.idleAnimations();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(Duration.ofMillis(120));

            assertTrue(pinch.isFocusMode());
            assertTrue(pager.isUserInputEnabled());
            assertEquals(0, root.getPaddingBottom());
            assertTrue(playerView.getLayoutParams().height > 0);
            assertTrue(playerView.getLayoutParams().height <= root.getHeight());
            assertSame(f.selectedPlayer.player, playerView.getPlayer());
            assertEquals(ownerBefore, owner.summary());

            assertTrue(f.feed.exitSensorFullscreenForBack());
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(Duration.ofMillis(120));

            assertFalse(pinch.isFocusMode());
            assertTrue(pager.isUserInputEnabled());
            assertEquals(originalBottomPadding, root.getPaddingBottom());
            assertEquals(originalPlayerHeight, playerView.getLayoutParams().height);
            assertSame(f.selectedPlayer.player, playerView.getPlayer());
            assertEquals(ownerBefore, owner.summary());
        }
    }

    @Test public void focusModeMediaTapTogglesPlaybackAndNeverStartsHoldSpeedBoost() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            View root = ((RecyclerView.ViewHolder) f.selected).itemView;
            ShitTokDynamicPinchController pinch = ReflectionHelpers.getField(f.selected, "pinch");

            ShitTokDynamicPinchTest.begin(root);
            ShitTokDynamicPinchTest.grow(root);
            ShitTokDynamicPinchTest.release(root, 200, 600);
            ShitTokDynamicPinchTest.idleAnimations();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(Duration.ofMillis(120));
            assertTrue(pinch.isFocusMode());

            f.selectedPlayer.readyToPlay = true;
            f.selectedPlayer.plays = 0;
            f.selectedPlayer.pauses = 0;
            f.selectedPlayer.speed = new androidx.media3.common.PlaybackParameters(1f);

            ShitTokDynamicPinchTest.touch(root, MotionEvent.ACTION_DOWN, 400, 600);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(Duration.ofMillis(650));
            assertEquals(1f, f.selectedPlayer.speed.speed, 0f);
            ShitTokDynamicPinchTest.touch(root, MotionEvent.ACTION_UP, 400, 600);
            assertEquals(1, f.selectedPlayer.pauses);
            assertFalse(f.selectedPlayer.readyToPlay);
            assertEquals(1f, f.selectedPlayer.speed.speed, 0f);

            ShitTokDynamicPinchTest.touch(root, MotionEvent.ACTION_DOWN, 400, 600);
            ShitTokDynamicPinchTest.touch(root, MotionEvent.ACTION_UP, 400, 600);
            assertEquals(1, f.selectedPlayer.plays);
            assertTrue(f.selectedPlayer.readyToPlay);
            assertEquals(1f, f.selectedPlayer.speed.speed, 0f);
        }
    }

    @Test public void focusModeControlRowClearsProgressAndUsesLargeTapTargets() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            View root = ((RecyclerView.ViewHolder) f.selected).itemView;
            ShitTokDynamicPinchController pinch = ReflectionHelpers.getField(f.selected, "pinch");

            ShitTokDynamicPinchTest.begin(root);
            ShitTokDynamicPinchTest.grow(root);
            ShitTokDynamicPinchTest.release(root, 200, 600);
            ShitTokDynamicPinchTest.idleAnimations();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(Duration.ofMillis(120));
            assertTrue(pinch.isFocusMode());

            FrameLayout focusChrome = ReflectionHelpers.getField(pinch, "focusChrome");
            SeekBar focusSeek = ReflectionHelpers.getField(pinch, "focusSeek");
            TextView focusPlay = ReflectionHelpers.getField(pinch, "focusPlay");
            TextView focusSpeed = ReflectionHelpers.getField(pinch, "focusSpeed");
            View pill = (View) focusPlay.getParent();

            FrameLayout.LayoutParams chromeParams =
                    (FrameLayout.LayoutParams) focusChrome.getLayoutParams();
            FrameLayout.LayoutParams seekParams =
                    (FrameLayout.LayoutParams) focusSeek.getLayoutParams();
            FrameLayout.LayoutParams pillParams =
                    (FrameLayout.LayoutParams) pill.getLayoutParams();

            int seekBottom = seekParams.topMargin + seekParams.height;
            int pillTop = chromeParams.height - pillParams.bottomMargin - pillParams.height;
            assertTrue("progress and control row must not overlap", pillTop > seekBottom);

            int minTouch = Math.round(48f * root.getResources().getDisplayMetrics().density);
            assertTrue(pillParams.height >= minTouch);
            assertTrue(pillParams.width >= minTouch * 2);
            // TextView setMinWidth/setMinHeight use different properties from View minimums.
            assertTrue(focusPlay.getMinWidth() >= minTouch);
            assertTrue(focusPlay.getMinHeight() >= minTouch);
            assertTrue(focusSpeed.getMinWidth() >= minTouch);
            assertTrue(focusSpeed.getMinHeight() >= minTouch);
        }
    }
}
