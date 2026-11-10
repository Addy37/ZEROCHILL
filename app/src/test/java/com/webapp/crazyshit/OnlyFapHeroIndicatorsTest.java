package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.atomic.AtomicInteger;

/** Tests the homepage indicator's seven-slot paging and tap target, with no sources loaded. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(application = android.app.Application.class, sdk = 35)
public final class OnlyFapHeroIndicatorsTest {
    @Test public void activeIndicatorWindowStaysInsideTheHeroPool() {
        assertEquals(0, OnlyFapHeroIndicators.startIndex(0, 3));
        assertEquals(0, OnlyFapHeroIndicators.startIndex(0, 8));
        assertEquals(1, OnlyFapHeroIndicators.startIndex(4, 8));
        assertEquals(1, OnlyFapHeroIndicators.startIndex(7, 8));
        assertEquals(3, OnlyFapHeroIndicators.startIndex(9, 10));
    }

    @Test public void tappingAnotherDotRequestsItsHeroWithoutChangingThePool() {
        OnlyFapHeroIndicators dots = new OnlyFapHeroIndicators(RuntimeEnvironment.getApplication());
        AtomicInteger tapped = new AtomicInteger(-1);
        dots.setListener(tapped::set);
        dots.show(0, 3, Color.CYAN, false);
        int w = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST);
        int h = View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST);
        dots.measure(w, h);
        dots.layout(0, 0, dots.getMeasuredWidth(), dots.getMeasuredHeight());
        float density = dots.getResources().getDisplayMetrics().density;
        float x = (dots.getWidth() - 3 * Math.round(density * 18)) / 2f
                + Math.round(density * 18) * 1.5f;
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                x, dots.getHeight() / 2f, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP,
                x, dots.getHeight() / 2f, 0);
        try {
            assertTrue(dots.onTouchEvent(down));
            assertTrue(dots.onTouchEvent(up));
            assertEquals(1, tapped.get());
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    @Test public void rippleIsNotAnInteractiveOrAccessibleLayer() {
        OnlyFapHeroRippleView ripple = new OnlyFapHeroRippleView(RuntimeEnvironment.getApplication());
        assertFalse(ripple.isClickable());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                ripple.getImportantForAccessibility());
        ripple.setAccent(Color.MAGENTA);
        ripple.reset();
    }
    @Test public void reducedMotionCommitsImmediatelyAndClampsShrinkingPool() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", false).commit();
        OnlyFapHeroIndicators dots = new OnlyFapHeroIndicators(context);
        dots.show(7, 8, Color.MAGENTA, true);
        dots.show(7, 2, Color.GREEN, true);
        assertEquals(1, (int) org.robolectric.util.ReflectionHelpers.getField(dots, "selected"));
        assertEquals(1f, (float) org.robolectric.util.ReflectionHelpers.getField(dots, "highlightedSlot"), .001f);
        org.junit.Assert.assertNull(org.robolectric.util.ReflectionHelpers.getField(dots, "animator"));
        assertEquals(Color.GREEN, (int) org.robolectric.util.ReflectionHelpers.getField(dots, "accent"));
        context.getSharedPreferences("app_prefs", 0).edit().remove("immersive_motion_enabled").commit();
    }

}
