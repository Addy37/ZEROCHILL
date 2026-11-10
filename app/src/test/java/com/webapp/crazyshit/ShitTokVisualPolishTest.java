package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.RuntimeEnvironment;
import static org.robolectric.Shadows.shadowOf;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36}, qualifiers = "w400dp-h800dp-xhdpi")
public class ShitTokVisualPolishTest {
    @Test public void pagingFadeTracksDirectionAndCancellationWithoutBlackFrames() {
        assertEquals(1f, ShitTokVisualMotion.pageAlpha(3, 3, 0f, true), 0f);
        assertEquals(.96f, ShitTokVisualMotion.pageAlpha(3, 3, 1f, true), .001f);
        assertEquals(.96f, ShitTokVisualMotion.pageAlpha(4, 3, 0f, true), .001f);
        assertEquals(1f, ShitTokVisualMotion.pageAlpha(4, 3, 1f, true), 0f);
        assertEquals(.98f, ShitTokVisualMotion.pageAlpha(3, 3, .5f, true), .001f);
        assertEquals(1f, ShitTokVisualMotion.pageAlpha(3, 3, 0f, true), 0f);
        assertEquals(1f, ShitTokVisualMotion.pageAlpha(3, 3, .5f, false), 0f);
        assertEquals(1f, ShitTokVisualMotion.pageAlpha(8, 3, .5f, true), 0f);
    }

    @Test public void boundHolderRefreshesAccentAndRebindClearsMotion() {
        try (ShitTokAudioOwnershipTest.Fixture fixture = new ShitTokAudioOwnershipTest.Fixture()) {
            Object holder = fixture.selected;
            FrameLayout root = (FrameLayout) ((RecyclerView.ViewHolder) holder).itemView;
            FrameLayout ring = ReflectionHelpers.getField(holder, "avatarRing");
            FrameLayout favoriteTarget = ReflectionHelpers.getField(holder, "creatorFavoriteTarget");
            TextView badge = ReflectionHelpers.getField(holder, "creatorFavoriteBadge");
            SeekBar seek = ReflectionHelpers.getField(holder, "seekBar");
            assertTrue(favoriteTarget.getLayoutParams().width >= 48 * root.getResources().getDisplayMetrics().density);

            AppearanceTheme.setAccent(fixture.host.get(), AppearanceTheme.Accent.PURPLE);
            ReflectionHelpers.callInstanceMethod(holder, "refreshVisualTheme");
            int purple = AppearanceTheme.Accent.PURPLE.color;
            assertEquals(purple, shadowOf((GradientDrawable) ring.getBackground()).getStrokeColor());
            assertEquals(purple, ((GradientDrawable) badge.getBackground()).getColor().getDefaultColor());
            assertEquals(purple, seek.getProgressTintList().getDefaultColor());

            AppearanceTheme.setAccent(fixture.host.get(), AppearanceTheme.Accent.GREEN);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));
            int green = AppearanceTheme.Accent.GREEN.color;
            assertEquals(green, shadowOf((GradientDrawable) ring.getBackground()).getStrokeColor());
            assertEquals(green, seek.getProgressTintList().getDefaultColor());

            ReflectionHelpers.callInstanceMethod(holder, "setSwipeAlpha",
                    ReflectionHelpers.ClassParameter.from(float.class, .96f));
            assertEquals(.96f, root.getAlpha(), .001f);
            View surface = ((androidx.media3.ui.PlayerView) ReflectionHelpers.getField(holder, "playerView"))
                    .getVideoSurfaceView();
            assertNotNull(surface);
            assertEquals(.96f, surface.getAlpha(), .001f);

            ViewPager2 pager = ReflectionHelpers.getField(fixture.feed, "pager");
            RecyclerView.Adapter adapter = pager.getAdapter();
            adapter.bindViewHolder((RecyclerView.ViewHolder) holder, 0);
            assertEquals(1f, root.getAlpha(), 0f);
            assertEquals(1f, surface.getAlpha(), 0f);
            assertEquals(green, ((GradientDrawable) badge.getBackground()).getColor().getDefaultColor());
            AppearanceTheme.setAccent(fixture.host.get(), AppearanceTheme.Accent.CYAN);
        }
    }

    @Test public void avatarAndFavoriteHaveSeparateReachableTargets() {
        try (ShitTokAudioOwnershipTest.Fixture fixture = new ShitTokAudioOwnershipTest.Fixture()) {
            Object holder = fixture.selected;
            FrameLayout control = ReflectionHelpers.getField(holder, "creatorAvatarControl");
            FrameLayout ring = ReflectionHelpers.getField(holder, "avatarRing");
            FrameLayout favorite = ReflectionHelpers.getField(holder, "creatorFavoriteTarget");
            int d = Math.round(control.getResources().getDisplayMetrics().density);
            control.setVisibility(View.VISIBLE);
            FrameLayout root = (FrameLayout) ((RecyclerView.ViewHolder) holder).itemView;
            root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 800, 1600);
            favorite.setClickable(true);
            assertTrue(ring.getBottom() < favorite.getTop());
            assertTrue(ring.getWidth() >= 48 * d);
            assertTrue(favorite.getWidth() >= 48 * d);
            android.widget.LinearLayout rail = ReflectionHelpers.getField(holder, "actionRail");
            Rect favoriteRect = new Rect(0, 0, favorite.getWidth(), favorite.getHeight());
            root.offsetDescendantRectToMyCoords(favorite, favoriteRect);
            View firstAction = rail.getChildAt(0);
            Rect firstActionRect = new Rect(0, 0, firstAction.getWidth(), firstAction.getHeight());
            root.offsetDescendantRectToMyCoords(firstAction, firstActionRect);
            assertFalse(Rect.intersects(favoriteRect, firstActionRect));
            AtomicInteger galleryClicks = new AtomicInteger();
            AtomicInteger favoriteClicks = new AtomicInteger();
            control.setOnClickListener(v -> galleryClicks.incrementAndGet());
            favorite.setOnClickListener(v -> favoriteClicks.incrementAndGet());
            tap(control, ring.getLeft() + ring.getWidth() / 2f,
                    ring.getTop() + ring.getHeight() / 2f);
            tap(control, favorite.getLeft() + favorite.getWidth() / 2f,
                    favorite.getTop() + favorite.getHeight() / 2f);
            assertEquals(1, galleryClicks.get());
            assertEquals(1, favoriteClicks.get());
        }
    }

    @Test public void railDefaultsWhiteAndActiveControlsUseAccent() {
        try (ShitTokAudioOwnershipTest.Fixture fixture = new ShitTokAudioOwnershipTest.Fixture()) {
            Object holder = fixture.selected;
            android.widget.LinearLayout rail = ReflectionHelpers.getField(holder, "actionRail");
            for (int i = 0; i < rail.getChildCount(); i++) {
                View action = rail.getChildAt(i);
                assertTrue(action.getLayoutParams().width >= 48 * action.getResources().getDisplayMetrics().density);
                assertTrue(action.getLayoutParams().height >= 48 * action.getResources().getDisplayMetrics().density);
                assertEquals(android.graphics.Color.WHITE,
                        ((TextView) action).getCompoundDrawableTintList().getDefaultColor());
            }
            AppearanceTheme.setAccent(fixture.host.get(), AppearanceTheme.Accent.BLUE);
            ReflectionHelpers.setField(fixture.feed, "autoScrollEnabled", true);
            ReflectionHelpers.callInstanceMethod(holder, "refreshVisualTheme");
            TextView auto = ReflectionHelpers.getField(holder, "autoScroll");
            assertEquals(AppearanceTheme.Accent.BLUE.color,
                    auto.getCompoundDrawableTintList().getDefaultColor());
            AppearanceTheme.setAccent(fixture.host.get(), AppearanceTheme.Accent.CYAN);
        }
    }

    @Test public void interruptedEntranceAndRecyclingRestoreSettledGeometry() {
        try (ShitTokAudioOwnershipTest.Fixture fixture = new ShitTokAudioOwnershipTest.Fixture()) {
            Object holder = fixture.selected;
            View lower = ReflectionHelpers.getField(holder, "lower");
            View landscapeHeader = ReflectionHelpers.getField(holder, "landscapeHeader");
            lower.setTranslationY(12f);
            lower.setAlpha(.86f);
            landscapeHeader.setTranslationY(12f);
            ReflectionHelpers.callInstanceMethod(holder, "syncOrientationChrome");
            assertEquals(0f, lower.getTranslationY(), 0f);
            assertEquals(0f, landscapeHeader.getTranslationY(), 0f);
            assertEquals(1f, lower.getAlpha(), 0f);

            ReflectionHelpers.callInstanceMethod(holder, "setSwipeAlpha",
                    ReflectionHelpers.ClassParameter.from(float.class, .96f));
            ViewPager2 pager = ReflectionHelpers.getField(fixture.feed, "pager");
            RecyclerView.Adapter adapter = pager.getAdapter();
            adapter.onViewRecycled((RecyclerView.ViewHolder) holder);
            assertEquals(1f, ((RecyclerView.ViewHolder) holder).itemView.getAlpha(), 0f);
            assertEquals(0f, lower.getTranslationY(), 0f);
        }
    }

    @Test public void reducedMotionSkipsEntranceAndPagingOpacity() {
        try (ShitTokAudioOwnershipTest.Fixture fixture = new ShitTokAudioOwnershipTest.Fixture()) {
            fixture.host.get().getSharedPreferences("app_prefs", 0).edit()
                    .putBoolean("immersive_motion_enabled", false).commit();
            Object holder = fixture.selected;
            View lower = ReflectionHelpers.getField(holder, "lower");
            ReflectionHelpers.callInstanceMethod(holder, "revealSettledControls");
            assertEquals(1f, lower.getAlpha(), 0f);
            assertEquals(0f, lower.getTranslationY(), 0f);
            assertEquals(1f, ShitTokVisualMotion.pageAlpha(0, 0, .5f,
                    ZeroChillMotion.animationsEnabled(fixture.host.get())), 0f);
            fixture.host.get().getSharedPreferences("app_prefs", 0).edit()
                    .putBoolean("immersive_motion_enabled", true).commit();
        }
    }

    @Test public void moreActionsUseGlassAbovePortraitRailAndBelowLandscapeHeader() {
        try (ShitTokAudioOwnershipTest.Fixture fixture = new ShitTokAudioOwnershipTest.Fixture()) {
            Object holder = fixture.selected;
            View root = ((RecyclerView.ViewHolder) holder).itemView;
            View portraitMore = root.findViewWithTag("shittok_more");
            ReflectionHelpers.callInstanceMethod(holder, "showMoreMenu",
                    ReflectionHelpers.ClassParameter.from(View.class, portraitMore));
            PopupWindow portraitPopup = shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
            GlassMenuTestSupport.assertGlass(portraitPopup);
            assertTrue(GlassMenuTestSupport.hasAction(portraitPopup, "Download"));
            assertTrue(GlassMenuTestSupport.hasAction(portraitPopup, "Report problem"));
            WindowManager.LayoutParams portraitPlacement =
                    (WindowManager.LayoutParams) portraitPopup.getContentView().getRootView().getLayoutParams();
            assertEquals(Gravity.BOTTOM, portraitPlacement.gravity & Gravity.VERTICAL_GRAVITY_MASK);
            portraitPopup.dismiss();

            RuntimeEnvironment.setQualifiers("w740dp-h360dp-land-xhdpi");
            ReflectionHelpers.callInstanceMethod(holder, "syncOrientationChrome");
            root.measure(View.MeasureSpec.makeMeasureSpec(1480, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1480, 720);
            View landscapeMore = root.findViewWithTag("shittok_landscape_more");
            assertEquals(View.VISIBLE, landscapeMore.getVisibility());
            ReflectionHelpers.callInstanceMethod(holder, "showMoreMenu",
                    ReflectionHelpers.ClassParameter.from(View.class, landscapeMore));
            PopupWindow landscapePopup = shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
            GlassMenuTestSupport.assertGlass(landscapePopup);
            assertTrue(GlassMenuTestSupport.hasAction(landscapePopup, "Video details"));
            WindowManager.LayoutParams landscapePlacement =
                    (WindowManager.LayoutParams) landscapePopup.getContentView().getRootView().getLayoutParams();
            assertEquals(Gravity.TOP, landscapePlacement.gravity & Gravity.VERTICAL_GRAVITY_MASK);
            landscapePopup.dismiss();
        }
    }

    private static void tap(View target, float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP, x, y, 0);
        target.dispatchTouchEvent(down);
        target.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        shadowOf(Looper.getMainLooper()).idle();
    }

}
