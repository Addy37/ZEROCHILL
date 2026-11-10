package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import androidx.appcompat.view.menu.MenuBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ZeroChillComposeMenuTest {
    @Test public void visibleItemsKeepOriginalMenuItemIdentityAndOrder() {
        MenuBuilder menu = new MenuBuilder(RuntimeEnvironment.getApplication());
        android.view.MenuItem first = menu.add(0, 101, 0, "Save");
        menu.add(0, 102, 1, "Hidden").setVisible(false);
        android.view.MenuItem last = menu.add(0, 103, 2, "Remove");
        last.setEnabled(false);
        java.util.List<android.view.MenuItem> visible = ZeroChillComposeMenu.visibleItems(menu);
        assertEquals(2, visible.size());
        assertSame(first, visible.get(0));
        assertSame(last, visible.get(1));
        assertFalse(visible.get(1).isEnabled());
        assertEquals(101, visible.get(0).getItemId());
    }

    @Test public void menuCallbackStillUsesOriginalModel() {
        MenuBuilder menu = new MenuBuilder(RuntimeEnvironment.getApplication());
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        menu.setCallback(new MenuBuilder.Callback() {
            @Override public boolean onMenuItemSelected(MenuBuilder source, android.view.MenuItem item) {
                calls.set(item.getItemId());
                return true;
            }
            @Override public void onMenuModeChange(MenuBuilder source) { }
        });
        android.view.MenuItem action = menu.add(0, 37, 0, "Profile");
        menu.performItemAction(action, 0);
        assertEquals(37, calls.get());
    }
    @Test @Config(sdk = {35, 36})
    public void nativeAndComposeMenusNotifyDismissalExactlyOnceAndReleaseDetachedAnchors() {
        try (org.robolectric.android.controller.ActivityController<android.app.Activity> controller =
                org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup()) {
            android.app.Activity activity = controller.get();
            android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
            android.widget.TextView anchor = new android.widget.TextView(activity);
            root.addView(anchor, new android.widget.FrameLayout.LayoutParams(96, 96));
            activity.setContentView(root);
            for (boolean nativeMenu : new boolean[]{true, false}) {
                java.util.concurrent.atomic.AtomicInteger dismissals = new java.util.concurrent.atomic.AtomicInteger();
                ZeroChillMenu menu = new ZeroChillMenu(activity, anchor);
                menu.getMenu().add("Menu item"); menu.setOnDismissListener(dismissals::incrementAndGet);
                if (nativeMenu) menu.showNative(); else menu.show();
                android.widget.PopupWindow popup = org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
                assertNotNull(popup); popup.dismiss(); popup.dismiss();
                assertEquals(1, dismissals.get());
                if (nativeMenu) menu.showNative(); else menu.show();
                root.removeView(anchor);
                assertEquals(2, dismissals.get());
                root.addView(anchor, new android.widget.FrameLayout.LayoutParams(96, 96));
            }
            java.util.concurrent.atomic.AtomicInteger skipped = new java.util.concurrent.atomic.AtomicInteger();
            ZeroChillMenu empty = new ZeroChillMenu(activity, anchor);
            empty.setOnDismissListener(skipped::incrementAndGet); empty.show();
            assertEquals(1, skipped.get());
        }
    }

    @Test @Config(sdk = {35, 36})
    public void standardActivityPopupOwnsItsWindowCompositionAndClosesCleanly() {
        try (org.robolectric.android.controller.ActivityController<android.app.Activity> controller =
                org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup()) {
            android.app.Activity activity = controller.get();
            android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
            android.widget.TextView anchor = new android.widget.TextView(activity);
            anchor.setText("Options");
            root.addView(anchor, new android.widget.FrameLayout.LayoutParams(96, 96));
            activity.setContentView(root);
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            for (int i = 0; i < 2; i++) {
                ZeroChillMenu menu = new ZeroChillMenu(activity, anchor);
                menu.getMenu().add("Change avatar");
                menu.getMenu().add("Hidden").setVisible(false);
                menu.setOnMenuItemClickListener(item -> { calls.incrementAndGet(); return true; });
                menu.show();
                android.widget.PopupWindow popup = org.robolectric.Shadows.shadowOf(
                        RuntimeEnvironment.getApplication()).getLatestPopupWindow();
                assertNotNull(popup);
                GlassMenuTestSupport.assertGlass(popup);
                androidx.lifecycle.LifecycleOwner owner = GlassMenuTestSupport.owner(popup);
                assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, owner.getLifecycle().getCurrentState());
                assertFalse(GlassMenuTestSupport.hasAction(popup, "Hidden"));
                assertTrue(GlassMenuTestSupport.click(popup, "Change avatar"));
                assertFalse(popup.isShowing());
                assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, owner.getLifecycle().getCurrentState());
                assertEquals(i + 1, calls.get());
                assertFalse(activity.isFinishing());
            }
            ZeroChillMenu menu = new ZeroChillMenu(activity, anchor);
            menu.getMenu().add("Detach test");
            menu.show();
            android.widget.PopupWindow popup = org.robolectric.Shadows.shadowOf(
                    RuntimeEnvironment.getApplication()).getLatestPopupWindow();
            GlassMenuTestSupport.assertGlass(popup);
            androidx.lifecycle.LifecycleOwner owner = GlassMenuTestSupport.owner(popup);
            root.removeView(anchor);
            assertFalse(popup.isShowing());
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, owner.getLifecycle().getCurrentState());
        }
    }

}
