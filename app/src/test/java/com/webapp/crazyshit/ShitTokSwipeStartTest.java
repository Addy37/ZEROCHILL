package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
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
        // Baseline diagnostic: report ownership before selecting a fix.
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
            Object holder = rv.getChildViewHolder(rv.getChildAt(0));
            PlayerView player = ReflectionHelpers.getField(holder, "playerView");
            View.OnTouchListener original = shadowOf(player).getOnTouchListener();
            assertTrue(original != null);
            player.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) childMoves++;
                boolean consumed = original.onTouch(v, event);
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
            feed.dispatchTouchEvent(event);
            event.recycle();
        }

        void finish() {
            feed.close();
            activity.finish();
        }
    }
}
