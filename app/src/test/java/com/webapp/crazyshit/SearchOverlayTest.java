package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SearchOverlayTest {
    @Test public void activationTypingClearAndBackPreserveUnderlyingView() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).create().start().resume();
        Activity activity = host.get();
        SearchGlassOwner owner = new SearchGlassOwner(activity);
        FrameLayout root = new FrameLayout(activity);
        ScrollView underlying = new ScrollView(activity);
        TextView page = new TextView(activity);
        page.setHeight(3000);
        underlying.addView(page);
        root.addView(underlying);
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
        underlying.scrollTo(0, 350);
        boolean[] dismissed = {false};
        SearchPanel panel = new SearchPanel(activity, new Intent(), true, () -> dismissed[0] = true);
        panel.create(null);
        root.addView(panel.view);
        assertSame(underlying, root.getChildAt(0));
        assertTrue(panel.field().hasFocus());
        View content = ReflectionHelpers.getField(panel, "content");
        assertEquals(View.INVISIBLE, content.getVisibility());
        panel.field().setText("a");
        View clear = ReflectionHelpers.getField(panel, "clear");
        assertEquals(View.VISIBLE, clear.getVisibility());
        assertEquals(View.VISIBLE, content.getVisibility());
        clear.performClick();
        assertEquals("", panel.field().getText().toString());
        assertEquals(View.GONE, clear.getVisibility());
        assertFalse(dismissed[0]);
        panel.back();
        assertTrue(dismissed[0]);
        root.removeView(panel.view);
        panel.close();
        assertEquals(350, underlying.getScrollY());
        assertSame(page, underlying.getChildAt(0));
        owner.close();
        host.pause().stop().destroy();
    }

    @Test public void overlayStateRestoresQueryAndScopeWithoutLaunchingDestination() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).create().start().resume();
        SearchPanel original = new SearchPanel(host.get(), new Intent().putExtra("scope", "bunkr"), true, () -> {});
        original.create(null);
        original.field().setText("a");
        Bundle saved = new Bundle();
        original.save(saved);
        original.close();
        SearchPanel restored = new SearchPanel(host.get(), new Intent().putExtra("scope", "bunkr"), true, () -> {});
        restored.create(saved);
        assertEquals("a", restored.field().getText().toString());
        assertEquals("Search creators", restored.field().getHint().toString());
        assertNull(org.robolectric.Shadows.shadowOf(host.get()).getNextStartedActivity());
        restored.close();
        host.pause().stop().destroy();
    }
}
