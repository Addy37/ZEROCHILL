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
    @Test public void focusModePreservesViewportAndRestoresChromeOnBack() {
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
            assertEquals(View.GONE, f.feed.findViewWithTag("shittok_source_control").getVisibility());
            assertTrue(pager.isUserInputEnabled());
            assertEquals(originalBottomPadding, root.getPaddingBottom());
            assertEquals("floating controls must not refit the picture",
                    originalPlayerHeight, playerView.getLayoutParams().height);
            assertSame(f.selectedPlayer.player, playerView.getPlayer());
            assertEquals(ownerBefore, owner.summary());

            assertTrue(f.feed.exitSensorFullscreenForBack());
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(Duration.ofMillis(120));

            assertFalse(pinch.isFocusMode());
            assertEquals(View.VISIBLE, f.feed.findViewWithTag("shittok_source_control").getVisibility());
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
            View pill = ((ShitTokFocusControls) focusChrome).pill;
            assertSame("controls belong above the pager, never to a scrolling holder",
                    f.feed, focusChrome.getParent());

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
    @Test public void focusButtonTapUpdatesOwnerAndPauseSurvivesPlaybackReconciliation() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            // The real shell places an alpha-hidden navbar above the feed. Without
            // this overlapping sibling, taps can pass in JVM tests and fail on-device.
            FrameLayout shell = (FrameLayout) f.feed.getParent();
            ZeroChillBottomNavigationView nav = new ZeroChillBottomNavigationView(f.host.get());
            nav.setAlpha(0f);
            shell.addView(nav, new FrameLayout.LayoutParams(-1, 112, android.view.Gravity.BOTTOM));
            layout(shell);
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            View root = ((RecyclerView.ViewHolder) f.selected).itemView;
            ShitTokDynamicPinchController pinch = ReflectionHelpers.getField(f.selected, "pinch");
            ShitTokDynamicPinchTest.begin(root);
            ShitTokDynamicPinchTest.grow(root);
            ShitTokDynamicPinchTest.release(root, 200, 600);
            ShitTokDynamicPinchTest.idleAnimations();
            layout(shell);
            ShitTokFocusControls controls = ReflectionHelpers.getField(pinch, "focusChrome");
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(f.feed, "playbackOwner");
            NativeContentItem item = ReflectionHelpers.getField(f.selected, "item");
            f.selectedPlayer.readyToPlay = true;
            tap(shell, controls.play);
            assertTrue(owner.isUserPaused(item.url));
            assertTrue((boolean) ReflectionHelpers.getField(f.selected, "userPaused"));
            assertFalse(f.selectedPlayer.readyToPlay);
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            assertFalse("feed reconciliation must respect Focus Mode pause", f.selectedPlayer.readyToPlay);
            tap(shell, controls.play);
            assertFalse(owner.isUserPaused(item.url));
            assertTrue(f.selectedPlayer.readyToPlay);
            assertTrue(pinch.isFocusMode());
        }
    }

    @Test public void controlsStayPinnedAndKeepSameInstanceAcrossSwipeAndBack() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            View root = ((RecyclerView.ViewHolder) f.selected).itemView;
            ShitTokDynamicPinchController first = ReflectionHelpers.getField(f.selected, "pinch");
            ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            ShitTokDynamicPinchTest.begin(root);
            ShitTokDynamicPinchTest.grow(root);
            ShitTokDynamicPinchTest.release(root, 200, 600);
            ShitTokDynamicPinchTest.idleAnimations();
            layout(f.feed);
            ShitTokFocusControls controls = ReflectionHelpers.getField(first, "focusChrome");
            int top = controls.getTop();
            assertSame(f.feed, controls.getParent());
            assertTrue(pager.beginFakeDrag());
            assertTrue(pager.fakeDragBy(-pager.getHeight()));
            assertEquals(top, controls.getTop());
            assertEquals(0f, controls.getTranslationY(), 0f);
            assertEquals(View.VISIBLE, controls.getVisibility());
            assertTrue(pager.endFakeDrag());
            ShitTokDynamicPinchTest.idleAnimations();
            RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
            RecyclerView.ViewHolder selected = recycler.findViewHolderForAdapterPosition(pager.getCurrentItem());
            ShitTokDynamicPinchController next = ReflectionHelpers.getField(selected, "pinch");
            assertTrue(next.isFocusMode());
            assertSame(controls, ReflectionHelpers.getField(next, "focusChrome"));
            assertSame(next, controls.owner);
            assertEquals(top, controls.getTop());
            assertTrue(f.feed.exitSensorFullscreenForBack());
            ShitTokDynamicPinchTest.idleAnimations();
            assertEquals(View.GONE, controls.getVisibility());
            assertNull(controls.owner);
        }
    }

    @Test public void focusEntryKeepsMeasuredPortraitAndSquarePictureBounds() {
        for (float aspect : new float[] {0.6f, 1f}) {
            try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
                ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
                View root = ((RecyclerView.ViewHolder) f.selected).itemView;
                PlayerView playerView = ReflectionHelpers.getField(f.selected, "playerView");
                androidx.media3.ui.AspectRatioFrameLayout picture = playerView.findViewById(
                        androidx.media3.ui.R.id.exo_content_frame);
                picture.setAspectRatio(aspect);
                layout(f.feed);
                android.graphics.Rect before = pictureBounds(f.feed, picture);
                assertTrue(before.width() > 0);
                assertTrue(before.height() > 0);
                ShitTokDynamicPinchTest.begin(root);
                ShitTokDynamicPinchTest.grow(root);
                ShitTokDynamicPinchTest.release(root, 200, 600);
                ShitTokDynamicPinchTest.idleAnimations();
                layout(f.feed);
                assertEquals("Focus entry must preserve visible size and vertical placement for " + aspect,
                        before, pictureBounds(f.feed, picture));
            }
        }
    }

    private static android.graphics.Rect pictureBounds(FrameLayout host, View picture) {
        android.graphics.Rect bounds = new android.graphics.Rect(0, 0, picture.getWidth(), picture.getHeight());
        host.offsetDescendantRectToMyCoords(picture, bounds);
        return bounds;
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 800, 1600);
    }

    private static void tap(FrameLayout host, View target) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        target.getDrawingRect(bounds);
        host.offsetDescendantRectToMyCoords(target, bounds);
        ShitTokDynamicPinchTest.touch(host, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY());
        ShitTokDynamicPinchTest.touch(host, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY());
        // View posts performClick after the touch sequence, as it does on a device.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

}
