package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-mdpi")
public class SearchRecentGlassTest {
    @Before public void reset() {
        var context = RuntimeEnvironment.getApplication();
        RecentSearchStore.clear(context);
        context.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", false).commit();
        android.provider.Settings.Global.putFloat(context.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    }

    @Test public void naturalHeightFitsZeroOneThreeAndOverflowWithImeAndSmallWindows() {
        for (int count : new int[]{0, 1, 3, 10}) {
            var host = Robolectric.buildActivity(Activity.class).setup();
            SearchGlassOwner owner = new SearchGlassOwner(host.get());
            RecentSearchStore.clear(host.get());
            for (int i = 0; i < count; i++) RecentSearchStore.remember(host.get(), "Query " + i);
            SearchPanel panel = new SearchPanel(host.get(), new Intent(), true, () -> {});
            panel.create(null); host.get().setContentView(panel.view);
            try {
                View pane = ReflectionHelpers.getField(panel, "resultSurface");
                View rows = ReflectionHelpers.getField(panel, "resultsControls");
                ScrollView scroll = ReflectionHelpers.getField(panel, "recentLayer");
                LinearLayout history = ReflectionHelpers.getField(panel, "recentPanel");
                assertEquals(count + 1, history.getChildCount());
                for (int windowHeight : new int[]{800, 420}) for (int ime : new int[]{0, 300}) {
                    ViewCompat.dispatchApplyWindowInsets(panel.view, insets(ime));
                    layout(panel.view, 400, windowHeight);
                    int gap = ime > 20 ? 8 : 12;
                    int available = Math.max(0, windowHeight - 24 - Math.max(20, ime) - 76 - gap);
                    int expected = Math.min(48 + 56 * count + 14, available);
                    assertEquals(expected, pane.getHeight());
                    assertEquals(pane.getHeight(), rows.getHeight());
                    assertEquals(pane.getTop(), rows.getTop());
                    assertEquals(24 + 76, pane.getTop());
                    assertEquals(count == 0 ? View.INVISIBLE : View.VISIBLE, pane.getVisibility());
                    assertTrue(rows.getClipToOutline());
                    assertEquals(new android.graphics.Rect(0, 0, rows.getWidth(), rows.getHeight()), rows.getClipBounds());
                    assertTrue(pane.getBottom() <= windowHeight - Math.max(20, ime) - gap);
                    if (count > 0 && available > 14) {
                        scroll.setSmoothScrollingEnabled(false);
                        scroll.fullScroll(View.FOCUS_DOWN); layout(panel.view, 400, windowHeight);
                        assertFalse(scroll.canScrollVertically(1));
                        assertEquals(Math.max(0, history.getHeight() - scroll.getHeight()), scroll.getScrollY());
                    }
                }
            } finally { panel.close(); owner.close(); host.pause().stop().destroy(); }
        }
    }

    @Test public void removalsAnimateFromCurrentBoundsAndInsetsCancelObsoleteTargets() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        SearchGlassOwner owner = new SearchGlassOwner(host.get());
        for (int i = 0; i < 3; i++) RecentSearchStore.remember(host.get(), "Query " + i);
        SearchPanel panel = new SearchPanel(host.get(), new Intent(), true, () -> {});
        panel.create(null); host.get().setContentView(panel.view);
        try {
            layout(panel.view, 400, 800);
            View pane = ReflectionHelpers.getField(panel, "resultSurface");
            assertEquals(230, pane.getHeight());
            host.get().getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", true).commit();
            RecentSearchStore.remove(host.get(), "Query 2"); panel.resume(); layout(panel.view, 400, 800);
            android.animation.ValueAnimator animator = ReflectionHelpers.getField(panel.view, "heightAnimator");
            assertNotNull(animator);
            animator.setCurrentPlayTime(90); layout(panel.view, 400, 800);
            assertTrue(pane.getHeight() > 174 && pane.getHeight() < 230);
            View rows = ReflectionHelpers.getField(panel, "resultsControls");
            assertEquals(pane.getHeight(), rows.getHeight());
            RecentSearchStore.remove(host.get(), "Query 1"); panel.resume(); layout(panel.view, 400, 800);
            ViewCompat.dispatchApplyWindowInsets(panel.view, insets(300)); layout(panel.view, 400, 420);
            assertNull(ReflectionHelpers.getField(panel.view, "heightAnimator"));
            assertEquals(12, pane.getHeight());
            ViewCompat.dispatchApplyWindowInsets(panel.view, insets(0)); layout(panel.view, 400, 800);
            assertEquals(118, pane.getHeight());
            host.get().getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", false).commit();
            RecentSearchStore.clear(host.get()); panel.resume();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
            layout(panel.view, 400, 800);
            assertNull(ReflectionHelpers.getField(panel.view, "heightAnimator"));
            assertEquals(View.INVISIBLE, pane.getVisibility());
        } finally { panel.close(); owner.close(); host.pause().stop().destroy(); }
    }

    @Test public void recreatedSearchUsesNaturalHeightOnItsFirstLayout() {
        var host = Robolectric.buildActivity(SearchActivity.class).setup();
        RecentSearchStore.remember(host.get(), "Persisted query"); host.get().panel.resume();
        Bundle state = new Bundle(); host.get().panel.save(state);
        host.pause().stop().destroy();
        var recreated = Robolectric.buildActivity(SearchActivity.class).create(state).start().resume().visible();
        try {
            layout(recreated.get().panel.view, 800, 400);
            View pane = ReflectionHelpers.getField(recreated.get().panel, "resultSurface");
            assertEquals(118, pane.getHeight());
            assertNull(ReflectionHelpers.getField(recreated.get().panel.view, "heightAnimator"));
        } finally { recreated.pause().stop().destroy(); }
    }

    private static WindowInsetsCompat insets(int ime) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(), Insets.of(0,24,0,20))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0,0,0,ime)).build();
    }
    private static void layout(View view, int width, int height) {
        for (int i = 0; i < 2; i++) {
            view.forceLayout();
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height);
        }
    }
}
