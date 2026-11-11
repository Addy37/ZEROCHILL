package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.util.TypedValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShowsWatchLaunchTest {
    private static final String PAGE = "https://example.com/shows/episode-1";

    @Test
    public void sourceIdentityRequiresCurrentAttachedVisibleArtwork() {
        Activity source = Robolectric.buildActivity(Activity.class).setup().get();
        ImageView art = attachedArtwork(source);
        art.setTag(R.id.shows_watch_source_page, PAGE);
        assertTrue(ShowsWatchLaunch.matchesSource(art, PAGE));
        art.setTag(R.id.shows_watch_source_page, PAGE + "-recycled");
        assertFalse(ShowsWatchLaunch.matchesSource(art, PAGE));
        art.setTag(R.id.shows_watch_source_page, PAGE);
        ((ViewGroup) art.getParent()).removeView(art);
        assertFalse(ShowsWatchLaunch.matchesSource(art, PAGE));
    }

    @Test
    public void missingAndReducedMotionArtworkUseScopedFallbackWithoutCapture() {
        Activity source = Robolectric.buildActivity(Activity.class).setup().get();
        Intent missing = new Intent(source, VideoDetailActivity.class);
        ShowsWatchLaunch.mark(missing, null, PAGE);
        assertEquals(ShowsWatchActivity.class.getName(), missing.getComponent().getClassName());
        assertNull(ShowsWatchLaunch.artwork(missing));

        ImageView art = attachedArtwork(source);
        art.setTag(R.id.shows_watch_source_page, PAGE);
        source.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE).edit()
                .putBoolean("immersive_motion_enabled", false).commit();
        Intent reduced = new Intent(source, VideoDetailActivity.class);
        ShowsWatchLaunch.mark(reduced, art, PAGE);
        assertEquals(ShowsWatchActivity.class.getName(), reduced.getComponent().getClassName());
        assertNull(ShowsWatchLaunch.artwork(reduced));
    }

    @Test
    public void capturedArtworkBelongsOnlyToMatchingLaunchAndIsReused() {
        Activity source = Robolectric.buildActivity(Activity.class).setup().get();
        source.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE).edit()
                .putBoolean("immersive_motion_enabled", true).commit();
        ImageView art = attachedArtwork(source);
        art.setTag(R.id.shows_watch_source_page, PAGE);
        Intent launch = new Intent(source, VideoDetailActivity.class);
        ShowsWatchLaunch.mark(launch, art, PAGE);
        Bitmap captured = ShowsWatchLaunch.artwork(launch);
        assertNotNull(captured);
        assertEquals(Color.CYAN, captured.getPixel(captured.getWidth() / 2,
                captured.getHeight() / 2));
        assertSame(captured, ShowsWatchLaunch.artwork(launch));
        assertNull(ShowsWatchLaunch.artwork(new Intent(source, ShowsWatchActivity.class)));
        ShowsWatchLaunch.release(launch);
        assertNull(ShowsWatchLaunch.artwork(launch));
    }

    @Test
    public void interruptedReverseTransformReturnsToOriginalGeometry() {
        ShowsWatchLaunch.Transform source = ShowsWatchLaunch.initialTransform(
                35, 210, 160, 90, 0, 24, 400, 225);
        assertNotNull(source);
        assertEquals(0.4f, source.scaleX, 0.001f);
        assertEquals(0.4f, source.scaleY, 0.001f);
        assertEquals(35f, source.at(0f).translationX, 0.001f);
        assertEquals(186f, source.at(0f).translationY, 0.001f);
        assertEquals(1f, source.at(1f).scaleX, 0.001f);
        assertEquals(0f, source.at(1f).translationY, 0.001f);
        assertNull(ShowsWatchLaunch.initialTransform(0, 0, 0, 90, 0, 0, 400, 225));
    }

    @Test
    public void relatedVideoCannotReturnIntoOriginalThumbnail() {
        Activity source = Robolectric.buildActivity(Activity.class).setup().get();
        Intent launch = new Intent(source, ShowsWatchActivity.class);
        launch.putExtra(PlayerActivity.EXTRA_PAGE_URL, PAGE);
        assertTrue(ShowsWatchLaunch.isOpeningPage(launch, PAGE));
        assertFalse(ShowsWatchLaunch.isOpeningPage(launch,
                "https://example.com/shows/episode-2"));
    }

    @Test
    @Config(sdk = 26)
    public void api26UsesOpaqueWatchWindowAndSkipsArtworkCapture() {
        Activity source = Robolectric.buildActivity(Activity.class).setup().get();
        ImageView art = attachedArtwork(source);
        art.setTag(R.id.shows_watch_source_page, PAGE);
        Intent launch = new Intent(source, VideoDetailActivity.class);
        ShowsWatchLaunch.mark(launch, art, PAGE);
        assertEquals(ShowsWatchActivity.class.getName(), launch.getComponent().getClassName());
        assertNull(ShowsWatchLaunch.artwork(launch));
        assertFalse(isWatchThemeTranslucent(source));
    }

    @Test
    @Config(sdk = 27)
    public void api27WatchWindowSupportsRetainedSource() {
        Activity source = Robolectric.buildActivity(Activity.class).setup().get();
        assertTrue(isWatchThemeTranslucent(source));
    }

    private static boolean isWatchThemeTranslucent(Activity source) {
        ContextThemeWrapper themed = new ContextThemeWrapper(source,
                R.style.Theme_ZeroChill_ShowsWatch);
        TypedValue value = new TypedValue();
        return themed.getTheme().resolveAttribute(android.R.attr.windowIsTranslucent,
                value, true) && value.data != 0;
    }

    private static ImageView attachedArtwork(Activity activity) {
        ImageView art = new ImageView(activity);
        art.setImageDrawable(new ColorDrawable(Color.CYAN));
        activity.setContentView(art);
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 400, 800);
        return art;
    }
}
