package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Smoke check that a standard Library Activity can open its native popup safely. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ZeroChillLibraryNativeMenuTest {
    @Test public void standardActivityCanShowNativeMenuWithoutReturningToLibrary() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        TextView anchor = new TextView(activity);
        anchor.setText("⋮");
        anchor.setContentDescription("Section options");
        root.addView(anchor, new FrameLayout.LayoutParams(96, 96));
        activity.setContentView(root);

        assertTrue(anchor.isAttachedToWindow());
        ZeroChillMenu menu = new ZeroChillMenu(activity, anchor);
        menu.getMenu().add(0, 42, 0, "Clear watch history");
        AtomicInteger selected = new AtomicInteger();
        menu.setOnMenuItemClickListener(item -> {
            selected.incrementAndGet();
            return true;
        });

        menu.showNative();

        assertFalse("Showing options must not finish the screen", activity.isFinishing());
        assertTrue(menu.getMenu().performIdentifierAction(42, 0));
        assertEquals(1, selected.get());
    }
}
