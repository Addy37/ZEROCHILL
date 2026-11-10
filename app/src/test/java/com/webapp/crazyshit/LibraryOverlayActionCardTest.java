package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.SystemClock;
import android.os.Looper;
import org.robolectric.Shadows;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** A Gallery overflow tap must not open the saved item or return to Library. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w360dp-h800dp")
public class LibraryOverlayActionCardTest {
    @Test public void overflowIsIndependentOfMediaClick() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger menus = new AtomicInteger();

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClickable(true);
        content.setOnClickListener(v -> opens.incrementAndGet());
        content.addView(new LibraryAspectMediaCard(activity),
                new LinearLayout.LayoutParams(-1, -2));

        TextView more = new TextView(activity);
        more.setText("⋮");
        more.setContentDescription("Options for saved item");
        more.setOnClickListener(v -> menus.incrementAndGet());

        FrameLayout tile = LibraryOverlayActionCard.wrap(activity, content, more);
        tile.measure(View.MeasureSpec.makeMeasureSpec(BrowseUi.dp(activity, 320), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        tile.layout(0, 0, tile.getMeasuredWidth(), tile.getMeasuredHeight());

        View target = tile.findViewWithTag("library-card-options");
        assertNotNull(target);
        assertSame(content, tile.getChildAt(0));
        assertEquals("Options for saved item", target.getContentDescription());
        assertTrue(target.isClickable());
        assertTrue(target.isFocusable());

        // Tap the outer edge of the full 48dp hit target (outside the 34dp text icon).
        float x = target.getLeft() + BrowseUi.dp(activity, 4);
        float y = target.getTop() + BrowseUi.dp(activity, 4);
        tap(tile, x, y);
        // Android posts touch click handling to the main queue. Robolectric must drain it.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Options should open", 1, menus.get());
        assertEquals("Media must not open", 0, opens.get());

        // The icon itself is clickable too.
        assertTrue(more.performClick());
        assertEquals(2, menus.get());
        assertEquals(0, opens.get());

        assertTrue(content.performClick());
        assertEquals(1, opens.get());
    }

    @Test public void mixedGalleryFindsNestedAspectCardAfterOverflowSeparation() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout content = new LinearLayout(activity);
        LibraryAspectMediaCard card = new LibraryAspectMediaCard(activity);
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        TextView more = new TextView(activity);
        more.setContentDescription("Options");
        FrameLayout wrapped = LibraryOverlayActionCard.wrap(activity, content, more);
        LibraryMixedMediaGrid gallery = new LibraryMixedMediaGrid(activity);
        gallery.setItems(Arrays.asList(wrapped), Arrays.asList("portrait"), Map.of("portrait", true));
        assertTrue(card.isPortrait());
        assertEquals(2, ((LinearLayout) gallery.getChildAt(0)).getChildCount());
        gallery.dispose();
    }

    private static void tap(View view, float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 80, MotionEvent.ACTION_UP, x, y, 0);
        try {
            assertTrue(view.dispatchTouchEvent(down));
            assertTrue(view.dispatchTouchEvent(up));
        } finally {
            down.recycle();
            up.recycle();
        }
    }
}
