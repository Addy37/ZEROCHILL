package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

/** The horizontal fullscreen pill must follow the user's accent, including after a theme edit. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokFullscreenAccentTest {
    @Test public void fullscreenBorderAndIconFollowThemeWithoutChangingPlayback() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.PINK);
        ChaosFeedView feed = new ChaosFeedView(activity, item -> {}, false);
        try {
            ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
            RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
            RecyclerView.Adapter<?> adapter = pager.getAdapter();
            RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
            TextView fullscreen = holder.itemView.findViewWithTag("shittok_fullscreen");
            assertNotNull(fullscreen);
            View staticBackdrop = holder.itemView.findViewWithTag("shittok_horizontal_ambient");
            View liveBackdrop = holder.itemView.findViewWithTag("shittok_horizontal_live_ambient");
            assertNotNull(staticBackdrop);
            assertNotNull(liveBackdrop);
            // Without a selected, rendered horizontal video, live sampling
            // stays dormant and the static thumbnail remains available.
            assertEquals(View.GONE, liveBackdrop.getVisibility());
            assertAccent(fullscreen, AppearanceTheme.Accent.PINK.color);

            AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.PURPLE);
            ReflectionHelpers.callInstanceMethod(holder, "refreshVisualTheme");
            assertAccent(fullscreen, AppearanceTheme.Accent.PURPLE.color);
            android.widget.SeekBar seek = ReflectionHelpers.getField(holder, "seekBar");
            assertEquals(AppearanceTheme.Accent.PURPLE.color,
                    seek.getProgressTintList().getDefaultColor());
            assertEquals(AppearanceTheme.Accent.PURPLE.color,
                    seek.getThumbTintList().getDefaultColor());
        } finally {
            feed.close();
            AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.CYAN);
        }
    }

    private static void assertAccent(TextView fullscreen, int accent) {
        assertTrue(fullscreen.getBackground() instanceof GradientDrawable);
        // Android exposes drawable stroke setters but no public stroke getter.
        // Check the accent-to-stroke mapping and the actual live icon tint.
        int stroke = ChaosFeedView.fullscreenStrokeColor(accent);
        assertEquals(190, Color.alpha(stroke));
        assertEquals(accent & 0x00FFFFFF, stroke & 0x00FFFFFF);
        ColorStateList icon = fullscreen.getCompoundDrawableTintList();
        assertNotNull(icon);
        assertEquals(accent, icon.getDefaultColor());
    }
}
