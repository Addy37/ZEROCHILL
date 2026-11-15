package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class AnchoredMenuLifecycleTest {
    /** Recycler parents may detach an anchor synchronously while PopupWindow positions it. */
    private static class RecyclingParent extends FrameLayout {
        int scrollRequests;
        RecyclingParent(Activity activity) { super(activity); }
        @Override public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
            scrollRequests++;
            removeView(child);
            return true;
        }
    }

    @Test public void dropdownDefersReentrantDismissUntilPositioningFinishes() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = host.get();
            RecyclingParent root = new RecyclingParent(activity);
            TextView anchor = new TextView(activity);
            root.addView(anchor, new FrameLayout.LayoutParams(80, 80));
            activity.setContentView(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 400, 800);
            PopupWindow popup = new AnchoredMenuPopup.Window(new TextView(activity), 200, 4000, true);
            anchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { }
                @Override public void onViewDetachedFromWindow(View view) { popup.dismiss(); }
            });
            AnchoredMenuPopup.show(popup, anchor, false);
            assertEquals(1, root.scrollRequests);
            assertFalse(anchor.isAttachedToWindow());
            assertFalse(popup.isShowing());
        }
    }

    @org.robolectric.annotation.Implements(PopupWindow.class)
    public static class ExpiredWindow extends org.robolectric.shadows.ShadowPopupWindow {
        static Runnable detach;
        @org.robolectric.annotation.Implementation
        protected void invokePopup(android.view.WindowManager.LayoutParams params) {
            detach.run();
            throw new android.view.WindowManager.BadTokenException("Window expired while positioning");
        }
    }

    @Test @Config(shadows = ExpiredWindow.class)
    public void reentrantDetachAndExpiredTokenNotifyOnceAcrossComposeAndNativeFallback() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = host.get();
            FrameLayout root = new FrameLayout(activity);
            TextView anchor = new TextView(activity);
            root.addView(anchor, new FrameLayout.LayoutParams(80, 80));
            activity.setContentView(root);
            ExpiredWindow.detach = () -> root.removeView(anchor);
            AtomicInteger dismissed = new AtomicInteger();
            ZeroChillMenu menu = new ZeroChillMenu(activity, anchor);
            menu.getMenu().add("Remove download");
            menu.setOnDismissListener(dismissed::incrementAndGet);
            menu.show();
            assertEquals(1, dismissed.get());
            assertFalse(anchor.isAttachedToWindow());
        } finally { ExpiredWindow.detach = null; }
    }

    @Test public void finishingHostRejectsBothPresentationsAndNotifiesOnce() {
        for (boolean nativeMenu : new boolean[]{false, true}) {
            try (var host = Robolectric.buildActivity(Activity.class).setup()) {
                Activity activity = host.get();
                TextView anchor = new TextView(activity);
                activity.setContentView(anchor);
                activity.finish(); // Anchor is still attached during the teardown interval.
                assertTrue(anchor.isAttachedToWindow());
                AtomicInteger dismissed = new AtomicInteger();
                ZeroChillMenu menu = new ZeroChillMenu(activity, anchor);
                menu.getMenu().add("Remove download");
                menu.setOnDismissListener(dismissed::incrementAndGet);
                if (nativeMenu) menu.showNative(); else menu.show();
                assertEquals(1, dismissed.get());
                PopupWindow popup = org.robolectric.Shadows.shadowOf(
                        org.robolectric.RuntimeEnvironment.getApplication()).getLatestPopupWindow();
                assertTrue(popup == null || !popup.isShowing());
            }
        }
    }
}
