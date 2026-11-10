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

    @Test @Config(sdk = {35, 36}, qualifiers = "w400dp-h800dp-mdpi")
    public void galleryButtonDispatchKeepsOffCenterTouchThroughPostedClick() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        OnlyFapHubView hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { }
            @Override public void onOpenCreator(NativeContentItem creator, android.view.View anchor) {
                CreatorGalleryTransition.start(activity,
                        new android.content.Intent(activity, NativeFeedBrowserActivity.class), anchor, creator);
            }
            @Override public void onSearch() { }
            @Override public void onViewAllFavorites() { }
        });
        NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Fixture", "https://example.com/fixture", "", "", "", "", "", "fixture");
        Class<?> candidateClass = Class.forName("com.webapp.crazyshit.OnlyFapHubView$HeroCandidate");
        java.lang.reflect.Constructor<?> constructor = candidateClass.getDeclaredConstructor(
                NativeContentItem.class, String.class, String.class);
        constructor.setAccessible(true);
        org.robolectric.util.ReflectionHelpers.setField(hub, "heroItem",
                constructor.newInstance(creator, "https://example.com/fixture.jpg", "https://example.com"));
        android.view.View button = org.robolectric.util.ReflectionHelpers.getField(hub, "heroAction");
        button.setVisibility(android.view.View.VISIBLE);
        activity.setContentView(hub);
        hub.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY));
        hub.layout(0, 0, 400, 800);
        android.graphics.Rect bounds = new android.graphics.Rect();
        button.getDrawingRect(bounds);
        hub.offsetDescendantRectToMyCoords(button, bounds);
        float x = bounds.left + 8f, y = bounds.centerY();
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
                android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 20,
                android.view.MotionEvent.ACTION_UP, x, y, 0);
        try {
            hub.dispatchTouchEvent(down);
            hub.dispatchTouchEvent(up);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(20, java.util.concurrent.TimeUnit.MILLISECONDS);
            android.content.Intent launched = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
            org.junit.Assert.assertNotNull(launched);
            assertEquals(x, launched.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, -1), .01f);
            assertEquals(y, launched.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, -1), .01f);
        } finally {
            down.recycle(); up.recycle(); hub.close(); activity.finish();
        }
    }

}
