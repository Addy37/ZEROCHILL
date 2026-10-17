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
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).create().start().resume().visible();
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
        int statusBar = BrowseUi.dp(activity, 24);
        android.view.WindowInsets initialInsets = new android.view.WindowInsets.Builder()
                .setInsets(android.view.WindowInsets.Type.statusBars(),
                        android.graphics.Insets.of(0, statusBar, 0, 0))
                .setVisible(android.view.WindowInsets.Type.statusBars(), true).build();
        // Keep framework traversals consistent with this window's simulated status bar.
        root.setOnApplyWindowInsetsListener((v, insets) -> initialInsets);
        android.widget.ImageView origin = new android.widget.ImageView(activity) {
            @Override public android.view.WindowInsets getRootWindowInsets() { return initialInsets; }
        };
        FrameLayout.LayoutParams originBounds = new FrameLayout.LayoutParams(44, 48,
                android.view.Gravity.TOP | android.view.Gravity.RIGHT);
        originBounds.topMargin = statusBar;
        root.addView(origin, originBounds);
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
        panel.view.setPadding(0, 0, 0, 0); // Simulate an overlay awaiting its first inset dispatch.
        panel.morph(origin, true, () -> {});
        // The overlay has not received a system inset dispatch of its own yet.
        assertEquals(statusBar, panel.view.getPaddingTop());
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                java.time.Duration.ofMillis(300));
        View header = ReflectionHelpers.getField(panel, "header");
        assertTrue(header.getHeight() > 0 && header.getHeight() < 200);
        int settledTop = statusBar + BrowseUi.dp(activity, 8);
        assertEquals(settledTop, header.getTop());
        // The later IME inset dispatch must not change the settled pill's origin.
        panel.view.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder(initialInsets)
                .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, 300))
                .setVisible(android.view.WindowInsets.Type.ime(), true).build());
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
        assertEquals(settledTop, header.getTop());
        View icon = ReflectionHelpers.getField(panel, "searchIcon");
        assertEquals(0f, icon.getTranslationX(), 0.01f);
        assertNull(header.getClipBounds());
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
        boolean[] collapsed = {false};
        panel.morph(origin, false, () -> collapsed[0] = true);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                java.time.Duration.ofMillis(300));
        assertTrue(collapsed[0]);
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
