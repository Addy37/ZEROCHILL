package com.webapp.crazyshit;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.transition.Fade;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w360dp-h800dp-port-mdpi")
public class ShowsWatchMotionTest {
    private static final String PAGE = "https://example.test/episode";

    private static final class Fixture {
        Activity source;
        ImageView artwork;
        ShowsWatchActivity watch;
        FrameLayout root;
        View player;
        View details;
        ImageView poster;
        Intent launch;
    }

    private Fixture fixture() {
        Fixture f = new Fixture();
        f.source = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout browse = new FrameLayout(f.source);
        f.artwork = new ImageView(f.source);
        f.artwork.setBackgroundColor(Color.CYAN);
        f.artwork.setTag(R.id.shows_watch_source_page, PAGE);
        browse.addView(f.artwork, new FrameLayout.LayoutParams(180, 102));
        f.source.setContentView(browse);
        browse.layout(0, 0, 360, 800);
        f.artwork.layout(20, 400, 200, 502);
        f.launch = new Intent(f.source, VideoDetailActivity.class)
                .putExtra(PlayerActivity.EXTRA_PAGE_URL, PAGE);
        ShowsWatchLaunch.markDeferred(f.launch, f.artwork);
        f.watch = Robolectric.buildActivity(ShowsWatchActivity.class).get();
        f.watch.setTheme(R.style.Theme_ZeroChill_ShowsWatch);
        f.root = new FrameLayout(f.watch);
        f.root.setClipChildren(false);
        f.root.setClipToPadding(false);
        f.player = new View(f.watch);
        f.details = new View(f.watch);
        f.poster = new ImageView(f.watch);
        f.root.addView(f.player, new FrameLayout.LayoutParams(360, 203));
        f.root.addView(f.details, new FrameLayout.LayoutParams(360, 500));
        f.root.addView(f.poster, new FrameLayout.LayoutParams(360, 203));
        f.watch.setContentView(f.root);
        f.root.layout(0, 0, 360, 800);
        f.player.layout(0, 80, 360, 283);
        f.details.layout(0, 283, 360, 783);
        f.poster.layout(0, 80, 360, 283);
        f.poster.setImageBitmap(ShowsWatchLaunch.artwork(f.launch));
        return f;
    }

    @Test
    public void firstFrameIsArtworkSizedWithBrowseVisibleAndDetailsFollowingBottom() {
        Fixture f = fixture();
        assertEquals(ShowsWatchActivity.class.getName(), f.launch.getComponent().getClassName());
        assertTrue(ShowsWatchLaunch.animateEntrance(f.watch, f.launch, f.root,
                f.player, f.details, f.poster, null));
        assertEquals(180f, f.player.getWidth() * f.player.getScaleX(), 1f);
        assertEquals(102f, f.player.getHeight() * f.player.getScaleY(), 1f);
        assertEquals(0f, f.details.getAlpha(), 0.001f);
        assertEquals(0, ((ColorDrawable) f.root.getBackground()).getAlpha());
        assertFalse(f.root.getClipChildren());
        assertFalse(f.root.getClipToPadding());
        assertNotNull(ShowsWatchLaunch.artwork(f.launch));
        ShowsWatchLaunch.cancel(f.root);
        ShowsWatchLaunch.release(f.launch);
    }

    @Test
    public void portraitArtworkKeepsItsOriginalPixelsAtFirstFrame() {
        Fixture f = fixture();
        f.artwork.layout(20, 400, 122, 580);
        Intent portrait = new Intent(f.source, VideoDetailActivity.class)
                .putExtra(PlayerActivity.EXTRA_PAGE_URL, PAGE);
        ShowsWatchLaunch.markDeferred(portrait, f.artwork);
        f.poster.setImageBitmap(ShowsWatchLaunch.artwork(portrait));
        assertTrue(ShowsWatchLaunch.animateEntrance(f.watch, portrait, f.root,
                f.player, f.details, f.poster, null));
        float[] matrix = new float[9];
        f.poster.getImageMatrix().getValues(matrix);
        float visibleBitmapWidth = ShowsWatchLaunch.artwork(portrait).getWidth() *
                matrix[Matrix.MSCALE_X] * f.player.getScaleX();
        float visibleBitmapHeight = ShowsWatchLaunch.artwork(portrait).getHeight() *
                matrix[Matrix.MSCALE_Y] * f.player.getScaleY();
        assertEquals(102f, visibleBitmapWidth, 1f);
        assertEquals(180f, visibleBitmapHeight, 1f);
        assertEquals(ImageView.ScaleType.MATRIX, f.poster.getScaleType());
        ShowsWatchLaunch.cancel(f.root);
        ShowsWatchLaunch.release(portrait);
    }

    @Test
    public void backReversesCurrentFrameWithoutResetOrSecondWaypoint() {
        Fixture f = fixture();
        assertTrue(ShowsWatchLaunch.animateEntrance(f.watch, f.launch, f.root,
                f.player, f.details, f.poster, null));
        Object motion = f.root.getTag(R.id.shows_watch_motion);
        ReflectionHelpers.setField(motion, "progress", 0.5f);
        ReflectionHelpers.callInstanceMethod(motion, "apply");
        float halfwayWidth = f.player.getWidth() * f.player.getScaleX();
        float halfwayY = f.player.getTranslationY();
        assertTrue(halfwayWidth > 180f && halfwayWidth < 360f);
        assertEquals(0.5f, f.details.getAlpha(), 0.001f);
        assertTrue(f.details.getTranslationY() != 0f);
        assertTrue(((ColorDrawable) f.root.getBackground()).getAlpha() > 0);
        assertTrue(ShowsWatchLaunch.animateExit(f.watch, f.launch, f.root,
                f.player, f.details, f.poster, () -> {}));
        assertEquals(halfwayWidth, f.player.getWidth() * f.player.getScaleX(), 1f);
        assertEquals(halfwayY, f.player.getTranslationY(), 1f);
        assertEquals(1f, f.poster.getAlpha(), 0.001f);
        ShowsWatchLaunch.cancel(f.root);
        ShowsWatchLaunch.release(f.launch);
    }

    @Test
    public void recycledOrScrolledAwayArtworkUsesFallbackAndRotationClearsMotion() {
        Fixture f = fixture();
        assertTrue(ShowsWatchLaunch.animateEntrance(f.watch, f.launch, f.root,
                f.player, f.details, f.poster, null));
        f.artwork.setTag(R.id.shows_watch_source_page, "other episode");
        assertFalse(ShowsWatchLaunch.animateExit(f.watch, f.launch, f.root,
                f.player, f.details, f.poster, () -> {}));
        ShowsWatchLaunch.cancel(f.root);
        assertNull(f.root.getTag(R.id.shows_watch_motion));
        assertFalse(f.player.getClipToOutline()); // caller restores geometry on rotation
        f.artwork.setTag(R.id.shows_watch_source_page, PAGE);
        f.artwork.setTranslationY(900f);
        assertNull(ShowsWatchLaunch.visibleBounds(f.artwork));
        ShowsWatchLaunch.release(f.launch);
    }

    @Test
    public void backDuringPosterDismissalPreservesCurrentOpacity() {
        Fixture f = fixture();
        assertTrue(ShowsWatchLaunch.animateEntrance(f.watch, f.launch, f.root,
                f.player, f.details, f.poster, null));
        Object motion = f.root.getTag(R.id.shows_watch_motion);
        ReflectionHelpers.setField(motion, "progress", 0.5f);
        ReflectionHelpers.callInstanceMethod(motion, "apply");
        f.poster.setAlpha(0.4f);
        assertTrue(ShowsWatchLaunch.animateExit(f.watch, f.launch, f.root,
                f.player, f.details, f.poster, () -> {}));
        assertEquals(0.4f, f.poster.getAlpha(), 0.01f);
        ShowsWatchLaunch.cancel(f.root);
        ShowsWatchLaunch.release(f.launch);
    }

    @Test
    public void disabledMotionDeclinesGeometry() {
        Fixture f = fixture();
        f.watch.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("immersive_motion_enabled", false).commit();
        assertFalse(ShowsWatchLaunch.animateEntrance(f.watch, f.launch, f.root,
                f.player, f.details, f.poster, null));
        assertEquals(1f, f.player.getScaleX(), 0f);
        assertNull(f.root.getTag(R.id.shows_watch_motion));
        ShowsWatchLaunch.release(f.launch);
    }

    @Test
    public void resolvedInBackgroundKeepsPosterAndExitBlocksLatePlayerCreation() {
        Fixture f = fixture();
        ReflectionHelpers.setField(f.watch, "showsOrigin", true);
        ReflectionHelpers.setField(f.watch, "deferredShowsResolve", false);
        ReflectionHelpers.setField(f.watch, "recoveryResumed", false);
        ReflectionHelpers.setField(f.watch, "mediaUrl", "https://example.test/video.mp4");
        ReflectionHelpers.setField(f.watch, "pageUrl", PAGE);
        Object artwork = f.poster.getDrawable();
        ReflectionHelpers.callInstanceMethod(f.watch, "startResolvedDeferredShowsPlaybackIfResumed");
        assertNull(ReflectionHelpers.getField(f.watch, "player"));
        assertSame(artwork, f.poster.getDrawable());
        ReflectionHelpers.setField(f.watch, "recoveryResumed", true);
        ReflectionHelpers.setField(f.watch, "showsSavedOnExit", true);
        ReflectionHelpers.callInstanceMethod(f.watch, "startResolvedDeferredShowsPlaybackIfResumed");
        assertNull(ReflectionHelpers.getField(f.watch, "player"));
        assertSame(artwork, f.poster.getDrawable());
        ShowsWatchLaunch.release(f.launch);
    }

    @Test
    public void globalThemeWindowAndPredictiveBackLeaveScopedHostTransparent() {
        Fixture f = fixture();
        f.root.setBackgroundColor(Color.TRANSPARENT);
        ReflectionHelpers.callStaticMethod(OledThemeController.class, "apply",
                ReflectionHelpers.ClassParameter.from(Activity.class, f.watch));
        assertEquals(0, ((ColorDrawable) f.root.getBackground()).getAlpha());
        View content = f.watch.findViewById(android.R.id.content);
        assertTrue(content.getBackground() == null ||
                ((ColorDrawable) content.getBackground()).getAlpha() == 0);
        VideoDetailTransitionPolish.apply(f.watch);
        assertNull(f.watch.getWindow().getEnterTransition());
        PredictiveBackPolish.attach(f.watch);
        Map<?, ?> callbacks = ReflectionHelpers.getStaticField(PredictiveBackPolish.class, "CALLBACKS");
        assertFalse(callbacks.containsKey(f.watch));
        VideoDetailActivity generic = Robolectric.buildActivity(VideoDetailActivity.class).get();
        generic.setTheme(R.style.Theme_CrazyShit);
        VideoDetailTransitionPolish.apply(generic);
        assertTrue(generic.getWindow().getEnterTransition() instanceof Fade);
        ShowsWatchLaunch.release(f.launch);
    }

    public static class BackProbe extends VideoDetailActivity {
        int finishes;
        @Override public void finish() { finishes++; super.finish(); }
    }

    @Test
    public void duplicateBackDuringExitOnlyFinishesOnce() {
        BackProbe activity = Robolectric.buildActivity(BackProbe.class).get();
        activity.setTheme(R.style.Theme_CrazyShit);
        ReflectionHelpers.setField(activity, "showsOrigin", true);
        activity.onBackPressed();
        activity.onBackPressed();
        assertEquals(1, activity.finishes);
    }
}
