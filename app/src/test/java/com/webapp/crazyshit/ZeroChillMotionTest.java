package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class ZeroChillMotionTest {
    @Test public void quickTapCompressesAndDetachingRestoresScale() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            FrameLayout root = new FrameLayout(controller.get());
            View button = new View(controller.get());
            button.setClickable(true);
            root.addView(button);
            controller.get().setContentView(root);
            controller.visible();
            root.layout(0, 0, 100, 100);
            button.layout(0, 0, 100, 100);
            ZeroChillMotion.installPressFeedback(button);

            tap(button);

            assertTrue(button.getScaleX() < .99f);
            root.removeView(button);
            assertEquals(1f, button.getScaleX(), 0f);
            assertEquals(1f, button.getScaleY(), 0f);
        }
    }

    @Test public void reducedMotionKeepsTheClickAndSkipsScaling() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE).edit()
                    .putBoolean("immersive_motion_enabled", false).commit();
            FrameLayout root = new FrameLayout(activity);
            View button = new View(activity);
            root.addView(button);
            activity.setContentView(root);
            controller.visible();
            root.layout(0, 0, 100, 100);
            int[] clicks = {0};
            button.setOnClickListener(view -> clicks[0]++);
            button.layout(0, 0, 100, 100);
            ZeroChillMotion.installPressFeedback(button);

            tap(button);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertEquals(1, clicks[0]);
            assertEquals(1f, button.getScaleX(), 0f);
        }
    }

    private static void tap(View view) {
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(now, now, action, 10f, 10f, 0);
            try {
                view.dispatchTouchEvent(event);
            } finally {
                event.recycle();
            }
        }
    }
}
