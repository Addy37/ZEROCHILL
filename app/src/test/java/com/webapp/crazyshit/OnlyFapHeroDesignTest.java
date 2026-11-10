package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.google.android.material.card.MaterialCardView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class OnlyFapHeroDesignTest {
    @Test
    public void cinematicHeroUsesTallerPortraitFriendlyHeight() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        OnlyFapHubView hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { }
            @Override public void onSearch() { }
            @Override public void onMore() { }
            @Override public void onViewAllFavorites() { }
        });
        activity.setContentView(hub);

        ScrollView scroll = (ScrollView) hub.getChildAt(0);
        LinearLayout content = (LinearLayout) scroll.getChildAt(0);
        HorizontalSwipeFrameLayout heroSwipe =
                (HorizontalSwipeFrameLayout) content.getChildAt(0);
        MaterialCardView hero = (MaterialCardView) heroSwipe.getChildAt(0);

        int expectedHeight = Math.round(640f * activity.getResources()
                .getDisplayMetrics().density);
        assertEquals(expectedHeight, heroSwipe.getLayoutParams().height);
        assertEquals(0f, hero.getRadius(), 0.01f);

        hub.close();
    }
    @Test @Config(sdk = {35, 36})
    public void galleryActionFollowsAllSavedAccentsAndRefreshesOnReturn() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        OnlyFapHubView hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { }
            @Override public void onSearch() { }
            @Override public void onViewAllFavorites() { }
        });
        android.widget.TextView action = org.robolectric.util.ReflectionHelpers.getField(hub, "heroAction");
        for (AppearanceTheme.Accent accent : AppearanceTheme.Accent.values()) {
            AppearanceTheme.setAccent(activity, accent);
            // Refresh without starting provider discovery; this is the normal appearance bind.
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(hub, "applyHeroActionAccent");
            android.graphics.drawable.GradientDrawable background =
                    (android.graphics.drawable.GradientDrawable) action.getBackground();
            assertEquals(accent.color, background.getColor().getDefaultColor());
            assertEquals(AppearanceTheme.onAccent(activity), action.getCurrentTextColor());
            org.junit.Assert.assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(
                    action.getCurrentTextColor(), accent.color) >= 4.5);
        }
        hub.close();
        activity.finish();
    }

}
