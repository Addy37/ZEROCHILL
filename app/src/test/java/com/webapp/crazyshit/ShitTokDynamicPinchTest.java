package com.webapp.crazyshit;

import android.app.Application;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi")
public class ShitTokDynamicPinchTest {
    static void touch(View root, int action, float... xy) {
        int count = xy.length / 2;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xy[i * 2];
            coords[i].y = xy[i * 2 + 1];
            coords[i].pressure = coords[i].size = 1f;
        }
        MotionEvent event = MotionEvent.obtain(0, 20, action, count, props, coords,
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
        root.dispatchTouchEvent(event);
        event.recycle();
    }

    static void begin(View root) {
        touch(root, MotionEvent.ACTION_DOWN, 300, 500);
        touch(root, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), 300, 500, 500, 500);
    }

    static void grow(View root) {
        touch(root, MotionEvent.ACTION_MOVE, 200, 500, 600, 500);
    }

    static void nudge(View root) {
        touch(root, MotionEvent.ACTION_MOVE, 280, 500, 520, 500);
    }

    static void release(View root, float left, float right) {
        touch(root, MotionEvent.ACTION_POINTER_UP | (1 << 8), left, 500, right, 500);
    }

    static void idleAnimations() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(Duration.ofMillis(260));
    }

    static final class Harness {
        final ShitTokDynamicPinchController.TouchFrame root =
                new ShitTokDynamicPinchController.TouchFrame(RuntimeEnvironment.getApplication());
        final View media = new View(root.getContext()), overlay = new View(root.getContext());
        final List<Boolean> transitions = new ArrayList<>();
        boolean eligible = true;
        int clicks, cancels, ups;
        final ShitTokDynamicPinchController pinch;

        Harness() {
            root.addView(media, new FrameLayout.LayoutParams(-1, -1));
            overlay.setAlpha(0.7f);
            pinch = new ShitTokDynamicPinchController(root, media, () -> eligible,
                    transitions::add, overlay);
            media.setOnClickListener(v -> clicks++);
            media.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) pinch.videoTouchStarted();
                if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) cancels++;
                if (e.getActionMasked() == MotionEvent.ACTION_UP) ups++;
                return false;
            });
            root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 800, 1600);
        }
    }

    @Test public void scaleClampsAndSofteningAddsResistance() {
        assertEquals(0.45f, ShitTokDynamicPinchController.clampedScale(1, 200), 0f);
        assertEquals(3.5f, ShitTokDynamicPinchController.clampedScale(1000, 200), 0f);
        assertEquals(0.5f, ShitTokDynamicPinchController.clampedScale(100, 200), 0f);
        assertEquals(1f, ShitTokDynamicPinchController.clampedScale(Float.NaN, 200), 0f);
        assertEquals(1f, ShitTokDynamicPinchController.clampedScale(100, 0), 0f);

        float outward = ShitTokDynamicPinchController.softenedScale(400, 200);
        assertTrue(outward > 1f && outward < 2f);
        float inward = ShitTokDynamicPinchController.softenedScale(100, 200);
        assertTrue(inward > 0.5f && inward < 1f);
        assertFalse(ShitTokDynamicPinchController.shouldEnterFocusMode(1.17f));
        assertTrue(ShitTokDynamicPinchController.shouldEnterFocusMode(1.18f));
    }

    @Test public void softenedScaleTracksMidpointWithoutResizingOrScalingChrome() {
        Harness h = new Harness();
        begin(h.root);
        grow(h.root);
        assertTrue(h.pinch.isActive());
        assertTrue(h.media.getScaleX() > 1f && h.media.getScaleX() < 2f);
        assertEquals(400f, h.media.getPivotX(), 0.5f);
        assertTrue(h.media.getPivotY() > 500f && h.media.getPivotY() < 800f);
        assertEquals(800, h.media.getWidth());
        assertFalse(h.media.isLayoutRequested());
        assertEquals(1f, h.overlay.getScaleX(), 0f);
        assertEquals(0f, h.overlay.getAlpha(), 0f);

        float expanded = h.media.getScaleX();
        touch(h.root, MotionEvent.ACTION_MOVE, 375, 500, 425, 500);
        assertTrue(h.media.getScaleX() < expanded);
        touch(h.root, MotionEvent.ACTION_MOVE, 0, 500, 800, 500);
        assertTrue(h.media.getScaleX() > 1f);
        assertFalse(h.media.isLayoutRequested());
        assertEquals(Arrays.asList(true), h.transitions);
    }

    @Test public void ordinaryReleaseAnimatesBackAndRestoresChrome() {
        Harness h = new Harness();
        begin(h.root);
        nudge(h.root);
        float transformed = h.media.getScaleX();
        assertTrue(transformed > 1f);
        release(h.root, 280, 520);
        assertFalse(h.pinch.isActive());
        idleAnimations();
        assertFalse(h.pinch.isFocusMode());
        assertEquals(1f, h.media.getScaleX(), 0.001f);
        assertEquals(1f, h.media.getScaleY(), 0.001f);
        assertEquals(0.7f, h.overlay.getAlpha(), 0f);
        touch(h.root, MotionEvent.ACTION_UP, 280, 500);
        assertEquals(0, h.clicks);
        assertEquals(1, h.cancels);
        assertEquals(Arrays.asList(true, false), h.transitions);
    }

    @Test public void outwardPinchPromotesIntoPersistentFocusMode() {
        Harness h = new Harness();
        begin(h.root);
        grow(h.root);
        release(h.root, 200, 600);
        idleAnimations();
        assertFalse(h.pinch.isActive());
        assertTrue(h.pinch.isFocusMode());
        assertEquals(1f, h.media.getScaleX(), 0.001f);
        assertEquals(0f, h.overlay.getAlpha(), 0f);
        assertEquals(Arrays.asList(true), h.transitions);

        h.pinch.reset();
        assertFalse(h.pinch.isFocusMode());
        assertEquals(0.7f, h.overlay.getAlpha(), 0f);
        assertEquals(Arrays.asList(true, false), h.transitions);
    }

    @Test public void cancelAndThirdFingerNeverEnterFocusMode() {
        for (int end : new int[] {MotionEvent.ACTION_CANCEL}) {
            Harness h = new Harness();
            begin(h.root);
            grow(h.root);
            touch(h.root, end, 200, 500, 600, 500);
            idleAnimations();
            assertFalse(h.pinch.isActive());
            assertFalse(h.pinch.isFocusMode());
            assertEquals(1f, h.media.getScaleX(), 0.001f);
            assertEquals(0.7f, h.overlay.getAlpha(), 0f);
        }

        Harness h = new Harness();
        begin(h.root);
        grow(h.root);
        touch(h.root, MotionEvent.ACTION_POINTER_DOWN | (2 << 8),
                200, 500, 600, 500, 700, 600);
        idleAnimations();
        assertFalse(h.pinch.isActive());
        assertFalse(h.pinch.isFocusMode());
        assertEquals(1f, h.media.getScaleX(), 0.001f);
    }

    @Test public void oneFingerAndIneligibleTwoFingerTouchesStayNormal() {
        Harness h = new Harness();
        touch(h.root, MotionEvent.ACTION_DOWN, 300, 500);
        touch(h.root, MotionEvent.ACTION_UP, 300, 500);
        // The unattached harness queues performClick until attachment. Check dispatch.
        assertEquals(1, h.ups);
        assertEquals(0, h.cancels);
        assertTrue(h.transitions.isEmpty());
        h.eligible = false;
        begin(h.root);
        grow(h.root);
        assertFalse(h.pinch.isActive());
        assertTrue(h.transitions.isEmpty());
    }

    @Test public void resetBeforeSecondFingerRejectsPreviousItemsTouch() {
        Harness h = new Harness();
        touch(h.root, MotionEvent.ACTION_DOWN, 300, 500);
        h.pinch.reset();
        touch(h.root, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), 300, 500, 500, 500);
        assertFalse(h.pinch.isActive());
        assertTrue(h.transitions.isEmpty());
    }

    @Test public void repeatedOrdinaryPinchesResetCleanly() {
        Harness h = new Harness();
        for (int i = 0; i < 20; i++) {
            begin(h.root);
            nudge(h.root);
            release(h.root, 280, 520);
            idleAnimations();
            assertEquals(1f, h.media.getScaleX(), 0.001f);
            assertFalse(h.pinch.isFocusMode());
        }
        assertEquals(40, h.transitions.size());
    }

    @Test public void parentInterceptionAndOverlayTouchesStaySeparate() {
        Harness h = new Harness();
        final boolean[] disallowed = {false};
        FrameLayout parent = new FrameLayout(h.root.getContext()) {
            @Override public void requestDisallowInterceptTouchEvent(boolean value) {
                disallowed[0] = value;
                super.requestDisallowInterceptTouchEvent(value);
            }
        };
        parent.addView(h.root);
        begin(h.root);
        assertTrue(disallowed[0]);
        grow(h.root);
        touch(h.root, MotionEvent.ACTION_CANCEL, 200, 500, 600, 500);
        idleAnimations();
        assertFalse(disallowed[0]);

        View button = new View(h.root.getContext());
        button.setClickable(true);
        h.root.addView(button, new FrameLayout.LayoutParams(-1, -1));
        h.root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        h.root.layout(0, 0, 800, 1600);
        begin(h.root);
        grow(h.root);
        assertFalse(h.pinch.isActive());
        assertFalse(disallowed[0]);
    }

    @Test public void autoScrollWaitsUntilFocusModeExits() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            View root = ((RecyclerView.ViewHolder) f.selected).itemView;
            ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            begin(root);
            grow(root);
            ReflectionHelpers.callInstanceMethod(f.feed, "requestAutoAdvance", from(int.class, 0));
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(0, pager.getCurrentItem());
            assertTrue(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));

            release(root, 200, 600);
            idleAnimations();
            ShitTokDynamicPinchController pinch = ReflectionHelpers.getField(f.selected, "pinch");
            assertTrue(pinch.isFocusMode());
            assertEquals(0, pager.getCurrentItem());
            assertFalse(pager.isUserInputEnabled());

            pinch.reset();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1, pager.getCurrentItem());

            begin(((RecyclerView.ViewHolder) f.duplicate).itemView);
            ShitTokDynamicPinchController stale = ReflectionHelpers.getField(f.duplicate, "pinch");
            assertFalse(stale.isActive());
        }
    }

    @Test public void realHolderFocusKeepsAudioAndSurfaceUntilBackExit() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
            View root = ((RecyclerView.ViewHolder) f.selected).itemView;
            PlayerView playerView = ReflectionHelpers.getField(f.selected, "playerView");
            View media = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame);
            ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(f.feed, "playbackOwner");
            String before = owner.summary();
            int plays = f.selectedPlayer.plays, pauses = f.selectedPlayer.pauses;

            begin(root);
            grow(root);
            ShitTokDynamicPinchController pinch = ReflectionHelpers.getField(f.selected, "pinch");
            assertTrue(pinch.isActive());
            assertFalse(pager.isUserInputEnabled());
            assertTrue(media.getScaleX() > 1f && media.getScaleX() < 2f);
            assertEquals(1f, playerView.getScaleX(), 0f);
            assertEquals(0f, ((View) ReflectionHelpers.getField(f.selected, "lower")).getAlpha(), 0f);
            assertSame(f.selectedPlayer.player, playerView.getPlayer());
            assertEquals(before, owner.summary());
            assertEquals(plays, f.selectedPlayer.plays);
            assertEquals(pauses, f.selectedPlayer.pauses);

            release(root, 200, 600);
            idleAnimations();
            assertTrue(pinch.isFocusMode());
            assertFalse(pager.isUserInputEnabled());
            assertEquals(1f, media.getScaleX(), 0.001f);
            assertSame(f.selectedPlayer.player, playerView.getPlayer());
            assertEquals(before, owner.summary());

            assertTrue(f.feed.exitSensorFullscreenForBack());
            assertFalse(pinch.isFocusMode());
            assertTrue(pager.isUserInputEnabled());
            assertEquals(1f, ((View) ReflectionHelpers.getField(f.selected, "lower")).getAlpha(), 0f);
            assertEquals(before, owner.summary());
        }
    }

    @Test public void holderRecycleBindDetachAndHostTransitionsResetBeforeReuse() {
        for (String transition : new String[] {"recycle", "detach", "bind", "pause", "inactive",
                "rotation", "page", "ownership", "refresh", "close", "back"}) {
            try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
                ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
                ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
                RecyclerView.ViewHolder holder = (RecyclerView.ViewHolder) f.selected;
                begin(holder.itemView);
                grow(holder.itemView);
                ShitTokDynamicPinchController pinch = ReflectionHelpers.getField(holder, "pinch");
                assertTrue(transition, pinch.isActive());
                switch (transition) {
                    case "recycle": ((RecyclerView.Adapter) pager.getAdapter()).onViewRecycled(holder); break;
                    case "detach": ((RecyclerView.Adapter) pager.getAdapter()).onViewDetachedFromWindow(holder); break;
                    case "bind": ((RecyclerView.Adapter) pager.getAdapter()).bindViewHolder(holder, 1); break;
                    case "pause": f.feed.onHostPause(); break;
                    case "inactive": f.feed.setActive(false); break;
                    case "rotation": f.feed.onConfigurationChanged(); break;
                    case "page": pager.setCurrentItem(1, false); break;
                    case "ownership": ((ShitTokPlaybackOwner) ReflectionHelpers.getField(
                            f.feed, "playbackOwner")).release(); break;
                    case "refresh": f.feed.refresh(); break;
                    case "close": f.feed.close(); break;
                    case "back": assertTrue(f.feed.exitSensorFullscreenForBack()); break;
                }
                assertFalse(transition, pinch.isActive());
                assertFalse(transition, pinch.isFocusMode());
                assertTrue(pager.isUserInputEnabled());
                PlayerView view = ReflectionHelpers.getField(holder, "playerView");
                assertEquals(1f, view.findViewById(
                        androidx.media3.ui.R.id.exo_content_frame).getScaleX(), 0f);
                assertEquals(1f, ((View) ReflectionHelpers.getField(holder, "lower")).getAlpha(), 0f);
            }
        }
    }
}
