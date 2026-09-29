package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;

/** Synthetic event-time traces, not device frame-time or decoder benchmarks. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi")
public class ShitTokSwipeStartTest {
    @Test public void tracePlainVerticalDrag() {
        Harness h = new Harness();
        h.drag(false);
        assertTrue("Control gesture must reach the real pager", h.dragAt >= 0);
        h.finish();
    }

    @Test public void traceTapThenVerticalDrag() {
        Harness h = new Harness();
        h.drag(true);
        assertEquals("Single-finger drag must never disable pager input", -1, h.disabledAt);
        assertEquals("Pager must grab the first move beyond its unchanged slop",
                140 + (h.pagingSlop + 2) * 4, h.dragAt);
        h.finish();
    }

    @Test public void smallTapMovementDoesNotStartPaging() {
        Harness h = new Harness();
        h.event(0, 0, MotionEvent.ACTION_DOWN, 400, 700);
        h.event(0, 16, MotionEvent.ACTION_MOVE, 400, 700 - h.slop / 2f);
        h.event(0, 40, MotionEvent.ACTION_UP, 400, 700 - h.slop / 2f);
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(-1, h.dragAt);
        assertEquals(1, h.clicks);
        h.finish();
    }

    @Test public void twoFingerPinchStillHidesAndRestoresChrome() {
        Harness h = new Harness();
        // Deliver one intact multi-pointer stream to the real PlayerView listener. The
        // synthetic viewport's separate chrome children can split widely spaced pointers.
        h.directPlayer = true;
        // Keep both spans above Android's density-dependent minimum scaling span.
        int minimum = ViewConfiguration.get(h.activity).getScaledMinimumScalingSpan();
        float unit = Math.max(200, minimum);
        h.event(0, 0, MotionEvent.ACTION_DOWN, 400, 400);
        h.pointers(0, 8, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), 400, 400 + unit * 5);
        h.pointers(0, 16, MotionEvent.ACTION_MOVE, 400, 400 + unit * 4);
        h.pointers(0, 24, MotionEvent.ACTION_MOVE, 400, 400 + unit * 2.5f);
        assertTrue(h.pinchTrace.toString(), ReflectionHelpers.<Boolean>getField(h.feed, "clearDisplay"));
        assertFalse(h.pager.isUserInputEnabled());
        h.pointers(0, 32, MotionEvent.ACTION_POINTER_UP | (1 << 8), 400, 400 + unit * 2.5f);
        h.event(0, 40, MotionEvent.ACTION_UP, 400, 400);
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(h.pager.isUserInputEnabled());
        assertEquals(0, h.clicks);

        h.event(1000, 1000, MotionEvent.ACTION_DOWN, 400, 400);
        h.pointers(1000, 1008, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), 400, 400 + unit * 2.5f);
        h.pointers(1000, 1016, MotionEvent.ACTION_MOVE, 400, 400 + unit * 3.5f);
        h.pointers(1000, 1024, MotionEvent.ACTION_MOVE, 400, 400 + unit * 5);
        assertFalse(h.pinchTrace.toString(), ReflectionHelpers.<Boolean>getField(h.feed, "clearDisplay"));
        h.pointers(1000, 1032, MotionEvent.ACTION_POINTER_UP | (1 << 8), 400, 400 + unit * 5);
        h.event(1000, 1040, MotionEvent.ACTION_UP, 400, 400);
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(h.pager.isUserInputEnabled());
        assertEquals(-1, h.dragAt);
        assertEquals(0, h.clicks);
        h.finish();
    }

    @Test public void horizontalCreatorSwipeStillOwnsAndReleasesInput() {
        Harness h = new Harness();
        ReflectionHelpers.setField(h.holder, "item", new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Fixture creator", "https://example.invalid/creator",
                "", "", "", "", "OnlyHaven", "OnlyHaven"));
        h.event(0, 0, MotionEvent.ACTION_DOWN, 400, 700);
        h.event(0, 16, MotionEvent.ACTION_MOVE, 300, 699);
        assertTrue(ReflectionHelpers.<Boolean>getField(h.holder, "creatorSwipeTracking"));
        assertFalse(h.pager.isUserInputEnabled());
        assertEquals(-1, h.dragAt);
        h.event(0, 32, MotionEvent.ACTION_CANCEL, 300, 699);
        assertTrue(h.pager.isUserInputEnabled());
        assertFalse(ReflectionHelpers.<Boolean>getField(h.holder, "creatorSwipeTracking"));
        assertEquals(0, h.clicks);
        h.finish();
    }

    private static final class Harness {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final ChaosFeedView feed = new ChaosFeedView(activity, item -> { });
        final ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
        final int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        final int pagingSlop = ViewConfiguration.get(activity).getScaledPagingTouchSlop();
        final long base = SystemClock.uptimeMillis() + 1000;
        long eventOffset;
        long dragAt = -1;
        long disabledAt = -1;
        long firstMoveAt = -1;
        long slopAt = -1;
        int childMoves;
        int clicks;
        Object holder;
        PlayerView player;
        boolean directPlayer;
        final StringBuilder pinchTrace = new StringBuilder();

        Harness() {
            // Stop source/player work; keep the actual adapter, PlayerView listener and pager.
            feed.close();
            ArrayList<NativeContentItem> items = ReflectionHelpers.getField(feed, "items");
            for (int i = 0; i < 4; i++) {
                items.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                        "Touch fixture " + i, "https://example.invalid/" + i,
                        "", "", "", ""));
            }
            pager.getAdapter().notifyDataSetChanged();
            activity.setContentView(feed);
            feed.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
            feed.layout(0, 0, 800, 1600);
            RecyclerView rv = (RecyclerView) pager.getChildAt(0);
            assertTrue(rv.getChildCount() > 0);
            holder = rv.getChildViewHolder(rv.getChildAt(0));
            player = ReflectionHelpers.getField(holder, "playerView");
            View.OnTouchListener original = shadowOf(player).getOnTouchListener();
            assertTrue(original != null);
            View.OnClickListener originalClick = shadowOf(player).getOnClickListener();
            player.setOnClickListener(v -> {
                clicks++;
                originalClick.onClick(v);
            });
            player.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) childMoves++;
                boolean consumed = original.onTouch(v, event);
                if (directPlayer) pinchTrace.append("action=").append(event.getActionMasked())
                        .append(" pointers=").append(event.getPointerCount())
                        .append(" input=").append(pager.isUserInputEnabled())
                        .append(" clear=").append(ReflectionHelpers.<Boolean>getField(feed, "clearDisplay"))
                        .append('\n');
                if (!pager.isUserInputEnabled() && disabledAt < 0) disabledAt = eventOffset;
                return consumed;
            });
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override public void onPageScrollStateChanged(int state) {
                    if (state == ViewPager2.SCROLL_STATE_DRAGGING && dragAt < 0) {
                        dragAt = eventOffset;
                    }
                }
            });
        }

        void drag(boolean afterTap) {
            if (afterTap) {
                event(0, 0, MotionEvent.ACTION_DOWN, 400, 700);
                event(0, 40, MotionEvent.ACTION_UP, 400, 700);
            }
            int down = 140;
            event(down, down, MotionEvent.ACTION_DOWN, 400, 700);
            // 2 px / 8 ms resolves scale recognition versus ViewPager's paging slop.
            for (int distance = 2; distance <= pagingSlop * 4; distance += 2) {
                int at = down + distance * 4;
                if (firstMoveAt < 0) firstMoveAt = at;
                if (distance > slop && slopAt < 0) slopAt = at;
                event(down, at, MotionEvent.ACTION_MOVE, 400, 700 - distance);
            }
            System.out.println("SWIPE_START afterTap=" + afterTap + " touchSlop=" + slop
                    + " pagingSlop=" + pagingSlop + " down=" + down
                    + " firstMove=" + firstMoveAt + " aboveSlop=" + slopAt
                    + " dragging=" + dragAt + " disabled=" + disabledAt
                    + " childMoves=" + childMoves + " input=" + pager.isUserInputEnabled());
            event(down, down + pagingSlop * 16 + 8, MotionEvent.ACTION_UP,
                    400, 700 - pagingSlop * 4);
            assertTrue("Gesture must restore input", pager.isUserInputEnabled());
        }

        void event(long down, long at, int action, float x, float y) {
            eventOffset = at;
            MotionEvent event = MotionEvent.obtain(base + down, base + at, action, x, y, 0);
            if (directPlayer) player.dispatchTouchEvent(event);
            else feed.dispatchTouchEvent(event);
            event.recycle();
        }

        void pointers(long down, long at, int action, float firstY, float secondY) {
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
            for (int i = 0; i < 2; i++) {
                properties[i] = new MotionEvent.PointerProperties();
                properties[i].id = i;
                properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                coords[i] = new MotionEvent.PointerCoords();
                coords[i].x = 400;
                coords[i].y = i == 0 ? firstY : secondY;
                coords[i].pressure = 1;
                coords[i].size = 1;
            }
            eventOffset = at;
            MotionEvent event = MotionEvent.obtain(base + down, base + at, action, 2,
                    properties, coords, 0, 0, 1, 1, 0, 0,
                    android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
            if (directPlayer) player.dispatchTouchEvent(event);
            else feed.dispatchTouchEvent(event);
            event.recycle();
        }

        void finish() {
            feed.close();
            activity.finish();
        }
    }
}
