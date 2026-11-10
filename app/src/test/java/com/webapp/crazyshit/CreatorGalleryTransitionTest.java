package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36}, qualifiers = "w400dp-h800dp-mdpi")
public class CreatorGalleryTransitionTest {
    private NativeContentItem creator() {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, "Creator", "https://example.com", "", "", "", "", "", "Creator");
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final FrameLayout liveGallery = new FrameLayout(activity);
        final TextView toolbar = new TextView(activity);
        final FrameLayout mediaGrid = new FrameLayout(activity);
        final CreatorGalleryReveal reveal;
        Fixture(boolean motion, boolean recreated) {
            activity.setTheme(R.style.Theme_ZeroChill_ShowsWatch);
            activity.setIntent(new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true));
            activity.getSharedPreferences("app_prefs", 0).edit()
                    .putBoolean("immersive_motion_enabled", motion).commit();
            toolbar.setText("Creator gallery");
            liveGallery.addView(toolbar, new FrameLayout.LayoutParams(-1, 56));
            FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(-1, -1);
            gridParams.topMargin = 56;
            liveGallery.addView(mediaGrid, gridParams);
            reveal = new CreatorGalleryReveal(activity, liveGallery,
                    CreatorGalleryTransition.shouldAnimate(activity, recreated));
            activity.setContentView(reveal);
            reveal.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            reveal.layout(0, 0, 400, 800);
        }
        void drawFirstFrame() { reveal.getViewTreeObserver().dispatchOnPreDraw(); }
        void close() { ((FrameLayout) reveal.getParent()).removeView(reveal); activity.finish(); }
    }

    @Test public void launchNeedsNoSourceImageAndSuppressesRapidRepeat() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Intent gallery = new Intent(activity, NativeFeedBrowserActivity.class);
        CreatorGalleryTransition.start(activity, gallery, null, creator());
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertTrue(CreatorGalleryTransition.isScoped(started));
        assertEquals(3, started.getExtras().size());
        CreatorGalleryTransition.start(activity, new Intent(activity, NativeFeedBrowserActivity.class),
                new View(activity), creator());
        assertNull(shadowOf(activity).getNextStartedActivity());
        activity.finish();
    }

    @Test public void offCenterTapSurvivesPostedClickAndIsConsumedByLaunch() {
        Fixture fixture = new Fixture(true, true);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(0, 0,
                android.view.MotionEvent.ACTION_UP, 37f, 641f, 0);
        CreatorGalleryTransition.recordTap(new android.view.ContextThemeWrapper(fixture.activity,
                R.style.Theme_CrazyShit), up);
        up.recycle();
        ShadowLooper.idleMainLooper(16L, TimeUnit.MILLISECONDS);
        CreatorGalleryTransition.start(fixture.activity, new Intent(fixture.activity, NativeFeedBrowserActivity.class),
                fixture.liveGallery, creator());
        Intent started = shadowOf(fixture.activity).getNextStartedActivity();
        assertEquals(37f, started.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, -1), 0f);
        assertEquals(641f, started.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, -1), 0f);
        ShadowLooper.idleMainLooper(701L, TimeUnit.MILLISECONDS);
        CreatorGalleryTransition.start(fixture.activity, new Intent(fixture.activity, NativeFeedBrowserActivity.class),
                fixture.liveGallery, creator());
        Intent second = shadowOf(fixture.activity).getNextStartedActivity();
        int[] location = new int[2];
        fixture.liveGallery.getLocationOnScreen(location);
        assertEquals(location[0] + 200f,
                second.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, -1), 0f);
        assertEquals(location[1] + 400f,
                second.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, -1), 0f);
        fixture.close();
    }

    @Test public void keyboardClearsRecentTouchAndStaleTouchUsesAnchorCenter() {
        Fixture fixture = new Fixture(true, true);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(0, 0,
                android.view.MotionEvent.ACTION_UP, 12f, 13f, 0);
        CreatorGalleryTransition.recordTap(fixture.activity, up);
        CreatorGalleryTransition.clearTap(fixture.activity);
        CreatorGalleryTransition.start(fixture.activity, new Intent(), fixture.liveGallery, creator());
        Intent keyboard = shadowOf(fixture.activity).getNextStartedActivity();
        int[] location = new int[2];
        fixture.liveGallery.getLocationOnScreen(location);
        assertEquals(location[0] + 200f,
                keyboard.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, -1), 0f);
        assertEquals(location[1] + 400f,
                keyboard.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, -1), 0f);
        ShadowLooper.idleMainLooper(701L, TimeUnit.MILLISECONDS);
        CreatorGalleryTransition.recordTap(fixture.activity, up);
        ShadowLooper.idleMainLooper(CreatorGalleryTransition.TAP_MAX_AGE_MS + 1L, TimeUnit.MILLISECONDS);
        CreatorGalleryTransition.start(fixture.activity, new Intent(), fixture.liveGallery, creator());
        Intent stale = shadowOf(fixture.activity).getNextStartedActivity();
        assertEquals(location[0] + 200f,
                stale.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, -1), 0f);
        up.recycle();
        fixture.close();
    }

    @Test public void originMapsScreenCoordinatesWithoutDoubleApplyingSafeInsetsAndSurvivesRecreation() {
        Fixture fixture = new Fixture(true, true);
        fixture.reveal.setPadding(0, 48, 0, 24);
        int[] location = new int[2];
        fixture.reveal.getLocationOnScreen(location);
        fixture.activity.getIntent()
                .putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, location[0] + 33.5f)
                .putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, location[1] + 72.5f);
        Intent restored = new Intent(fixture.activity.getIntent());
        android.graphics.PointF point = CreatorGalleryTransition.resolveOrigin(fixture.reveal, restored);
        assertEquals(33.5f, point.x, 0f);
        assertEquals(72.5f, point.y, 0f);
        assertFalse(CreatorGalleryTransition.shouldAnimate(fixture.activity, true));
        restored.putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, -999f)
                .putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, 99999f);
        point = CreatorGalleryTransition.resolveOrigin(fixture.reveal, restored);
        assertEquals(0f, point.x, 0f);
        assertEquals(800f, point.y, 0f);
        restored.putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, Float.NaN);
        point = CreatorGalleryTransition.resolveOrigin(fixture.reveal, restored);
        assertEquals(200f, point.x, 0f);
        assertEquals(376f, point.y, 0f);
        fixture.close();
    }

    @Test public void openingContainsWholeLiveGalleryWithNoImageBoundsMorph() {
        Fixture fixture = new Fixture(true, false);
        assertEquals(View.INVISIBLE, fixture.reveal.getVisibility());
        assertSame(fixture.liveGallery, fixture.reveal.getChildAt(0));
        assertEquals(400, fixture.liveGallery.getWidth());
        assertEquals(800, fixture.liveGallery.getHeight());
        assertTrue(fixture.reveal.getChildAt(1) instanceof OnlyFapHeroRippleView);
        assertEquals(Color.BLACK, ((ColorDrawable) fixture.reveal.getBackground()).getColor());
        fixture.drawFirstFrame();
        // Loading and new content remain live while the root reveal is running.
        fixture.toolbar.setText("Gallery updated during reveal");
        TextView loadedMedia = new TextView(fixture.activity);
        fixture.mediaGrid.addView(loadedMedia);
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        assertEquals(View.VISIBLE, fixture.reveal.getVisibility());
        assertEquals("Gallery updated during reveal", fixture.toolbar.getText().toString());
        assertSame(fixture.mediaGrid, loadedMedia.getParent());
        assertEquals(0f, fixture.liveGallery.getTranslationX(), 0f);
        assertEquals(0f, fixture.liveGallery.getTranslationY(), 0f);
        assertEquals(1f, fixture.liveGallery.getScaleX(), 0f);
        assertEquals(1f, fixture.liveGallery.getScaleY(), 0f);
        fixture.close();
    }

    @Test public void circleUsesHeroCenterAndCoversAllFourGalleryCorners() {
        float radius = CreatorGalleryReveal.coverRadius(400, 800, 200, 376);
        for (int x : new int[]{0, 400}) for (int y : new int[]{0, 800}) {
            assertTrue(Math.hypot(x - 200, y - 376) <= radius + .001f);
        }
        assertEquals(650L, CreatorGalleryReveal.DURATION_MS);
    }

    @Test public void reducedMotionAndRecreationRevealImmediatelyWithoutReplay() {
        Fixture reduced = new Fixture(false, false);
        assertEquals(View.VISIBLE, reduced.reveal.getVisibility());
        reduced.drawFirstFrame();
        assertNull(ReflectionHelpers.getField(reduced.reveal, "animator"));
        reduced.close();
        Fixture recreated = new Fixture(true, true);
        assertEquals(View.VISIBLE, recreated.reveal.getVisibility());
        recreated.drawFirstFrame();
        assertNull(ReflectionHelpers.getField(recreated.reveal, "animator"));
        recreated.close();
    }

    @Test public void pauseDuringOpeningSettlesWholeTreeAndRepeatedCleanupIsSafe() {
        Fixture fixture = new Fixture(true, false);
        fixture.drawFirstFrame();
        fixture.reveal.cancelAndShow(); // Activity.onPause/onStop path without closing.
        fixture.reveal.cancelAndShow();
        assertEquals(View.VISIBLE, fixture.reveal.getVisibility());
        assertNull(ReflectionHelpers.getField(fixture.reveal, "animator"));
        assertSame(fixture.reveal, fixture.liveGallery.getParent());
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        assertEquals(View.VISIBLE, fixture.reveal.getVisibility());
        fixture.close();
    }

    @Test public void closingReversesNativeCircleAndHidesTreeBeforeFinishingOnce() {
        Fixture fixture = new Fixture(true, true);
        final int[] finishes = {0};
        assertTrue(fixture.reveal.close(() -> {
            assertEquals(View.INVISIBLE, fixture.reveal.getVisibility());
            finishes[0]++;
        }));
        android.animation.Animator animator = ReflectionHelpers.getField(fixture.reveal, "animator");
        assertNotNull(animator);
        assertTrue(animator.getInterpolator() instanceof android.view.animation.AccelerateInterpolator);
        assertEquals(550L, animator.getDuration());
        OnlyFapHeroRippleView closingRipple = (OnlyFapHeroRippleView) fixture.reveal.getChildAt(1);
        android.animation.Animator closingRings = ReflectionHelpers.getField(closingRipple, "animator");
        assertEquals(animator.getDuration(), closingRings.getDuration());
        final int[] mediaOpens = {0};
        fixture.toolbar.setOnClickListener(view -> mediaOpens[0]++);
        android.view.MotionEvent down = android.view.MotionEvent.obtain(0, 0,
                android.view.MotionEvent.ACTION_DOWN, 10f, 10f, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(0, 1,
                android.view.MotionEvent.ACTION_UP, 10f, 10f, 0);
        assertTrue(fixture.reveal.dispatchTouchEvent(down));
        assertTrue(fixture.reveal.dispatchTouchEvent(up));
        down.recycle(); up.recycle();
        assertTrue(fixture.reveal.dispatchKeyEvent(new android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)));
        assertEquals(0, mediaOpens[0]);
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                fixture.reveal.getImportantForAccessibility());
        assertTrue(fixture.reveal.close(() -> fail("Repeated Back must not replace completion")));
        fixture.reveal.cancelAndShow(); // Pause/stop/detach during an active close.
        fixture.reveal.cancelAndShow();
        assertEquals(1, finishes[0]);
        assertEquals(View.INVISIBLE, fixture.reveal.getVisibility());
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        assertEquals(1, finishes[0]);
        assertEquals(View.INVISIBLE, fixture.reveal.getVisibility());
        fixture.close();
    }

    @Test public void backDuringOpeningUsesCurrentScaledRingProgressAndSameOrigin() {
        Fixture fixture = new Fixture(true, false);
        int[] location = new int[2];
        fixture.reveal.getLocationOnScreen(location);
        fixture.activity.getIntent()
                .putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, location[0] + 35f)
                .putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, location[1] + 611f);
        fixture.drawFirstFrame();
        OnlyFapHeroRippleView ripple = (OnlyFapHeroRippleView) fixture.reveal.getChildAt(1);
        android.animation.ValueAnimator rings = ReflectionHelpers.getField(ripple, "animator");
        assertNotNull(rings);
        rings.setCurrentFraction(.25f);
        float progress = ripple.progress();
        final int[] finishes = {0};
        assertTrue(fixture.reveal.close(() -> finishes[0]++));
        android.animation.Animator reverse = ReflectionHelpers.getField(fixture.reveal, "animator");
        assertNotNull(reverse);
        assertEquals(Math.round(550L * progress), reverse.getDuration());
        assertEquals(progress, ripple.progress(), .001f);
        assertEquals(35f, (Float) ReflectionHelpers.getField(ripple, "originX"), 0f);
        assertEquals(611f, (Float) ReflectionHelpers.getField(ripple, "originY"), 0f);
        fixture.reveal.cancelAndShow();
        assertEquals(1, finishes[0]);
        fixture.close();
    }

    @Test public void backBeforeFirstDrawFinishesWithoutShowingWholeGallery() {
        Fixture fixture = new Fixture(true, false);
        final int[] finishes = {0};
        assertTrue(fixture.reveal.close(() -> finishes[0]++));
        assertEquals(1, finishes[0]);
        assertEquals(View.INVISIBLE, fixture.reveal.getVisibility());
        fixture.reveal.cancelAndShow();
        assertEquals(View.INVISIBLE, fixture.reveal.getVisibility());
        fixture.close();
    }

    @Test public void reducedMotionCloseLeavesFadeFinishingToCaller() {
        Fixture fixture = new Fixture(false, true);
        assertFalse(fixture.reveal.close(() -> fail("Fallback caller must own finish")));
        assertEquals(View.VISIBLE, fixture.reveal.getVisibility());
        assertNull(ReflectionHelpers.getField(fixture.reveal, "animator"));
        fixture.close();
    }

    @Test public void missedCloseCallbackHasBoundedExactlyOnceHiddenCompletion() {
        Fixture fixture = new Fixture(true, true);
        final int[] finishes = {0};
        assertTrue(fixture.reveal.close(() -> finishes[0]++));
        android.animation.Animator reverse = ReflectionHelpers.getField(fixture.reveal, "animator");
        reverse.removeAllListeners(); // Inject a missed native completion callback.
        ShadowLooper.idleMainLooper(CreatorGalleryReveal.FALLBACK_MS + 100L, TimeUnit.MILLISECONDS);
        assertEquals(1, finishes[0]);
        assertEquals(View.INVISIBLE, fixture.reveal.getVisibility());
        fixture.reveal.cancelAndShow();
        assertEquals(1, finishes[0]);
        fixture.close();
    }

    @Test public void detachBeforeFirstDrawCancelsPendingRevealWithoutReplay() {
        Fixture fixture = new Fixture(true, false);
        FrameLayout parent = (FrameLayout) fixture.reveal.getParent();
        parent.removeView(fixture.reveal);
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        parent.addView(fixture.reveal);
        fixture.drawFirstFrame();
        assertEquals(View.VISIBLE, fixture.reveal.getVisibility());
        assertNull(ReflectionHelpers.getField(fixture.reveal, "animator"));
        fixture.close();
    }

    @Test public void deadlineCannotLeaveWholeGalleryInvisibleWithoutFirstDraw() {
        Fixture fixture = new Fixture(true, false);
        ShadowLooper.idleMainLooper(CreatorGalleryReveal.FALLBACK_MS + 100L, TimeUnit.MILLISECONDS);
        assertEquals(View.VISIBLE, fixture.reveal.getVisibility());
        assertNull(ReflectionHelpers.getField(fixture.reveal, "animator"));
        fixture.close();
    }

    @Test public void safeInsetsKeepGalleryPaddedAndRippleConcentricWithWholeRoot() {
        Fixture fixture = new Fixture(true, false);
        fixture.reveal.setPadding(0, 48, 0, 24);
        fixture.reveal.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        fixture.reveal.layout(0, 0, 400, 800);
        View ripple = fixture.reveal.getChildAt(1);
        assertEquals(0, ripple.getLeft());
        assertEquals(0, ripple.getTop());
        assertEquals(400, ripple.getRight());
        assertEquals(800, ripple.getBottom());
        assertEquals(48, fixture.liveGallery.getTop());
        assertEquals(776, fixture.liveGallery.getBottom());
        assertEquals(728, fixture.liveGallery.getHeight());
        fixture.close();
    }

    @Test public void resizeDuringOpeningSettlesInsteadOfKeepingStaleNativeClip() {
        Fixture fixture = new Fixture(true, false);
        fixture.drawFirstFrame();
        fixture.reveal.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        fixture.reveal.layout(0, 0, 600, 400);
        assertEquals(View.VISIBLE, fixture.reveal.getVisibility());
        assertNull(ReflectionHelpers.getField(fixture.reveal, "animator"));
        assertEquals(600, fixture.liveGallery.getWidth());
        assertEquals(400, fixture.liveGallery.getHeight());
        fixture.close();
    }

    @Test public void animatorScaleExtendsStartedAnimationDeadlineButNotNoFrameBound() {
        Fixture fixture = new Fixture(true, true);
        android.provider.Settings.Global.putFloat(fixture.activity.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 2f);
        assertEquals(1700L, CreatorGalleryReveal.animationDeadline(fixture.activity, 650L));
        assertEquals(1050L, CreatorGalleryReveal.FALLBACK_MS);
        android.provider.Settings.Global.putFloat(fixture.activity.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        fixture.close();
    }

    @Test public void delayedOledPassesCannotPaintOutsideScopedRevealRoot() {
        Intent scoped = new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true);
        NativeFeedBrowserActivity activity = Robolectric.buildActivity(OnlyFapGalleryActivity.class, scoped).get();
        activity.setTheme(R.style.Theme_ZeroChill_ShowsWatch);
        FrameLayout content = new FrameLayout(activity);
        activity.setContentView(content);
        View outer = activity.findViewById(android.R.id.content);
        outer.setBackgroundColor(Color.TRANSPARENT);
        assertTrue(CreatorGalleryTransition.ownsRevealWindow(activity));
        OledThemeController.applySoon(activity);
        ShadowLooper.idleMainLooper(600L, TimeUnit.MILLISECONDS);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) outer.getBackground()).getColor());
        activity.finish();
    }
}
