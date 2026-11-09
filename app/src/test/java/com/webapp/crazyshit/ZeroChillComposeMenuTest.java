package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Activity;
import androidx.appcompat.view.menu.MenuBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
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
}
