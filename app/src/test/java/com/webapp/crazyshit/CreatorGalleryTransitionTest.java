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
        assertEquals(1, started.getExtras().size());
        CreatorGalleryTransition.start(activity, new Intent(activity, NativeFeedBrowserActivity.class),
                new View(activity), creator());
        assertNull(shadowOf(activity).getNextStartedActivity());
        activity.finish();
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

    @Test public void backDuringOpeningSettlesWholeTreeAndRepeatedCleanupIsSafe() {
        Fixture fixture = new Fixture(true, false);
        fixture.drawFirstFrame();
        fixture.reveal.cancelAndShow(); // Same path as Back and Activity.onStop.
        fixture.reveal.cancelAndShow();
        assertEquals(View.VISIBLE, fixture.reveal.getVisibility());
        assertNull(ReflectionHelpers.getField(fixture.reveal, "animator"));
        assertSame(fixture.reveal, fixture.liveGallery.getParent());
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        assertEquals(View.VISIBLE, fixture.reveal.getVisibility());
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

    @Test public void delayedOledPassesCannotPaintOutsideScopedRevealRoot() {
        Intent scoped = new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true);
        NativeFeedBrowserActivity activity = Robolectric.buildActivity(NativeFeedBrowserActivity.class, scoped).get();
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
