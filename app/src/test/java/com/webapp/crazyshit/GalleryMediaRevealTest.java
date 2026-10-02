package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Looper;
import android.view.View;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.util.UnstableApi;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.request.transition.Transition;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;
import java.util.Collections;

import static org.junit.Assert.*;

@OptIn(markerClass = UnstableApi.class)
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class GalleryMediaRevealTest {
    private Activity activity;
    private ZoomableImageView image;
    private ProgressBar progress;
    private GalleryMediaReveal reveal;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        setMotion(true);
        FrameLayout root = new FrameLayout(activity);
        image = new ZoomableImageView(activity);
        progress = new ProgressBar(activity);
        progress.setVisibility(View.GONE);
        root.addView(image);
        root.addView(progress);
        activity.setContentView(root);
        reveal = new GalleryMediaReveal(image, progress);
    }

    @After public void tearDown() {
        reveal.cancelAndSettle();
        setMotion(true);
        activity.finish();
    }

    @Test public void fastLoadsNeverFlashSpinnerAndSlowLoadsKeepOneDeadline() {
        reveal.setLoading(true);
        idle(150);
        assertEquals(View.GONE, progress.getVisibility());
        reveal.setLoading(false);
        idle(300);
        assertEquals(View.GONE, progress.getVisibility());
        reveal.setLoading(true);
        idle(150);
        reveal.setLoading(true); // A status update must not restart the delay.
        idle(80);
        assertEquals(View.VISIBLE, progress.getVisibility());
        reveal.setLoading(false);
        assertEquals(View.GONE, progress.getVisibility());
    }

    @Test public void remoteImageCrossfadesPreviewAndReturnSettlesFinalDrawable() {
        Drawable preview = bitmap();
        Drawable full = bitmap();
        image.setImageDrawable(preview);
        assertTrue(reveal.imageTransition(DataSource.REMOTE, true).transition(full, target()));
        assertTrue(image.getDrawable() instanceof TransitionDrawable);
        reveal.cancelAndSettle();
        assertSame(full, image.getDrawable());
        assertEquals(1f, image.getAlpha(), 0f);
    }

    @Test public void cachedImagesReducedMotionAndSharedExpansionSkipRevealAnimation() {
        for (DataSource source : new DataSource[]{DataSource.MEMORY_CACHE,
                DataSource.RESOURCE_DISK_CACHE, DataSource.DATA_DISK_CACHE, DataSource.LOCAL}) {
            assertFalse(reveal.imageTransition(source, true).transition(bitmap(), target()));
        }
        assertFalse(reveal.imageTransition(DataSource.REMOTE, false).transition(bitmap(), target()));
        setMotion(false);
        assertFalse(reveal.imageTransition(DataSource.REMOTE, true).transition(bitmap(), target()));
    }

    @Test public void settlingCrossfadePreservesPinchZoomAndDisplayedBounds() {
        Drawable preview = new BitmapDrawable(activity.getResources(),
                Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888));
        Drawable full = bitmap();
        image.setImageDrawable(preview);
        reveal.imageTransition(DataSource.REMOTE, true).transition(full, target());
        idle(1);
        ReflectionHelpers.setField(image, "zoom", 2f);
        android.graphics.Matrix matrix = ReflectionHelpers.getField(image, "zoomMatrix");
        matrix.postScale(2f, 2f);
        image.setImageMatrix(matrix);
        Drawable beforeDrawable = image.getDrawable();
        android.graphics.RectF before = new android.graphics.RectF(0, 0,
                beforeDrawable.getIntrinsicWidth(), beforeDrawable.getIntrinsicHeight());
        image.getImageMatrix().mapRect(before);
        reveal.cancelAndSettle();
        idle(300); // No drawable-swap reset may run alongside the return capture.
        android.graphics.RectF after = new android.graphics.RectF(0, 0,
                full.getIntrinsicWidth(), full.getIntrinsicHeight());
        image.getImageMatrix().mapRect(after);
        assertEquals(2f, (float) ReflectionHelpers.getField(image, "zoom"), 0f);
        assertEquals(before, after);
        assertSame(full, image.getDrawable());
    }

    @Test public void posterFadeCanBeInterruptedByReturnWithoutHidingRestoredPoster() {
        Drawable poster = bitmap();
        image.setImageDrawable(poster);
        reveal.revealVideo(true);
        idle(60);
        reveal.cancelAndSettle();
        reveal.showPoster();
        idle(400);
        assertSame(poster, image.getDrawable());
        assertEquals(View.VISIBLE, image.getVisibility());
        assertEquals(1f, image.getAlpha(), 0f);
    }

    @Test public void posterDisappearsAfterFadeAndReducedMotionRevealsImmediately() {
        image.setImageDrawable(bitmap());
        reveal.revealVideo(true);
        idle(400);
        assertEquals(View.GONE, image.getVisibility());
        reveal.showPoster();
        setMotion(false);
        reveal.revealVideo(true);
        assertEquals(View.GONE, image.getVisibility());
        assertEquals(1f, image.getAlpha(), 0f);
    }

    @Test public void cancelledLoadingCannotShowSpinnerOnRecycledOrExitedMedia() {
        reveal.setLoading(true);
        idle(100);
        reveal.cancelAndSettle();
        idle(400);
        assertEquals(View.GONE, progress.getVisibility());
    }

    @Test public void loadingPayloadPreservesDrawableImageRequestAndZoom() {
        BunkrGalleryPagerAdapter adapter = adapter(NativeContentItem.KIND_IMAGE);
        BunkrGalleryPagerAdapter.Holder holder = holder(adapter);
        Drawable loaded = bitmap();
        holder.image.setImageDrawable(loaded);
        idle(1);
        ReflectionHelpers.setField(holder.image, "zoom", 2f);
        int request = holder.imageRequest;
        adapter.setLoading(0, true);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertSame(loaded, holder.image.getDrawable());
        assertEquals(request, holder.imageRequest);
        assertEquals(2f, (float) ReflectionHelpers.getField(holder.image, "zoom"), 0f);
        adapter.setLoading(0, false);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        idle(400);
        assertEquals(View.GONE, holder.progress.getVisibility());
        adapter.onViewRecycled(holder);
    }

    @Test public void videoPosterStaysOverLiveSurfaceUntilFirstFrameAndSurvivesRelease() {
        setMotion(false);
        BunkrGalleryPagerAdapter adapter = adapter(NativeContentItem.KIND_MEDIA);
        BunkrGalleryPagerAdapter.Holder holder = holder(adapter);
        Drawable poster = bitmap();
        holder.image.setImageDrawable(poster);
        Player player = new IdlePlayer();
        adapter.activateVideo(0, player);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        FrameLayout root = (FrameLayout) holder.itemView;
        assertTrue(root.indexOfChild(holder.image) > root.indexOfChild(holder.playerView));
        assertEquals(View.VISIBLE, holder.image.getVisibility());
        assertEquals(View.VISIBLE, holder.playerView.getVisibility());
        adapter.onVideoBuffering(0, player, false); // STATE_READY alone cannot remove the poster.
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertEquals(View.VISIBLE, holder.image.getVisibility());
        adapter.onVideoFirstFrame(0, player);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertEquals(View.GONE, holder.image.getVisibility());
        int request = holder.imageRequest;
        adapter.clearActiveVideo();
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertSame(poster, holder.image.getDrawable());
        assertEquals(request, holder.imageRequest);
        assertEquals(View.VISIBLE, holder.image.getVisibility());
        assertEquals(1f, holder.image.getAlpha(), 0f);
        adapter.onViewRecycled(holder);
        player.release();
    }

    @Test public void staleVideoFrameAndBufferingCallbacksCannotRevealNewPlayerOrExitedViewer() {
        BunkrGalleryPagerAdapter adapter = adapter(NativeContentItem.KIND_MEDIA);
        Player old = new IdlePlayer();
        Player current = new IdlePlayer();
        adapter.activateVideo(0, old);
        adapter.activateVideo(0, current);
        adapter.onVideoFirstFrame(0, old);
        adapter.onVideoFirstFrame(1, current);
        adapter.onVideoBuffering(0, old, false);
        assertFalse(ReflectionHelpers.getField(adapter, "activeVideoFrameRendered"));
        assertTrue(ReflectionHelpers.getField(adapter, "activeVideoBuffering"));
        adapter.stopReveals();
        adapter.onVideoFirstFrame(0, current);
        assertFalse(ReflectionHelpers.getField(adapter, "activeVideoFrameRendered"));
        old.release();
        current.release();
    }

    @Test public void wideDrawableFitsBeforeAnyPostedWorkAndRefitsOnRotation() {
        image.layout(0, 0, 720, 1280);
        Drawable wide = new BitmapDrawable(activity.getResources(),
                Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888));
        image.setImageDrawable(wide);
        assertFitted(image, wide, 0f, 460f, 720f, 820f);
        image.layout(0, 0, 1280, 720);
        assertFitted(image, wide, 0f, 40f, 1280f, 680f);
    }

    @Test public void drawableArrivingBeforeLayoutIsCenteredOnFirstLayout() {
        Drawable wide = new BitmapDrawable(activity.getResources(),
                Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888));
        ZoomableImageView fresh = new ZoomableImageView(activity);
        fresh.setImageDrawable(wide);
        fresh.layout(0, 0, 720, 1280);
        assertFitted(fresh, wide, 0f, 460f, 720f, 820f);
    }

    @Test public void crossfadeFitsPortraitPreviewAndWideFullImageIndependently() {
        image.layout(0, 0, 720, 1280);
        Drawable preview = new BitmapDrawable(activity.getResources(),
                Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888));
        Drawable full = new BitmapDrawable(activity.getResources(),
                Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888));
        image.setImageDrawable(preview);
        reveal.imageTransition(DataSource.REMOTE, true).transition(full, target());
        assertTrue(image.getDrawable() instanceof GalleryFitCrossFade);
        assertEquals(full.getIntrinsicWidth(), image.getDrawable().getIntrinsicWidth());
        assertEquals(full.getIntrinsicHeight(), image.getDrawable().getIntrinsicHeight());
        GalleryFitCrossFade fade = (GalleryFitCrossFade) image.getDrawable();
        android.graphics.RectF previewBounds = new android.graphics.RectF(fade.fittedLayerBounds(0));
        image.getImageMatrix().mapRect(previewBounds);
        assertEquals(40f, previewBounds.left, 2f);
        assertEquals(0f, previewBounds.top, 2f);
        assertEquals(680f, previewBounds.right, 2f);
        assertEquals(1280f, previewBounds.bottom, 2f);
        android.graphics.RectF fullBounds = new android.graphics.RectF(fade.fittedLayerBounds(1));
        image.getImageMatrix().mapRect(fullBounds);
        assertEquals(460f, fullBounds.top, 2f);
        assertEquals(820f, fullBounds.bottom, 2f);
        image.layout(0, 0, 1280, 720);
        previewBounds = new android.graphics.RectF(fade.fittedLayerBounds(0));
        image.getImageMatrix().mapRect(previewBounds);
        assertEquals(460f, previewBounds.left, 2f);
        assertEquals(0f, previewBounds.top, 2f);
        assertEquals(820f, previewBounds.right, 2f);
        assertEquals(720f, previewBounds.bottom, 2f);
        reveal.cancelAndSettle();
        assertSame(full, image.getDrawable());
        assertFitted(image, full, 0f, 40f, 1280f, 680f);
    }

    @Test public void blurSnapshotKeepsPreviewBlurSeparateFromSharpFullImage() {
        image.layout(0, 0, 720, 1280);
        Drawable preview = bitmap();
        Drawable full = bitmap();
        image.setImageDrawable(preview);
        reveal.setLoading(true);
        assertTrue(image.hasLoadingBlur());
        reveal.imageTransition(DataSource.REMOTE, true).transition(full, target());
        assertFalse(image.hasLoadingBlur());
        TransitionDrawable fade = (TransitionDrawable) image.getDrawable();
        assertNotSame(preview, fade.getDrawable(0));
        assertSame(full, fade.getDrawable(1));
        Bitmap copy = ((BitmapDrawable) fade.getDrawable(0)).getBitmap();
        assertTrue(copy.getWidth() <= 128 && copy.getHeight() <= 128);
        assertFalse(((BitmapDrawable) preview).getBitmap().isRecycled());
        reveal.cancelAndSettle();
        assertFalse(image.hasLoadingBlur());
    }

    @Config(sdk = 28)
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test public void olderAndroidBlurSoftensOwnedSnapshotWithoutModifyingSourcePixels() {
        Bitmap source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        source.eraseColor(android.graphics.Color.BLACK);
        for (int y = 0; y < 32; y++) for (int x = 16; x < 32; x++) {
            source.setPixel(x, y, android.graphics.Color.WHITE);
        }
        image.layout(0, 0, 32, 32);
        image.setImageDrawable(new BitmapDrawable(activity.getResources(), source));
        reveal.setLoading(true);
        Bitmap result = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        image.draw(new android.graphics.Canvas(result));
        int softened = android.graphics.Color.red(result.getPixel(15, 16));
        assertTrue(softened > 0 && softened < 255);
        assertEquals(android.graphics.Color.BLACK, source.getPixel(15, 16));
        assertEquals(android.graphics.Color.WHITE, source.getPixel(16, 16));
        reveal.setLoading(false);
        assertFalse(image.hasLoadingBlur());
    }

    @Test public void sharedMotionSkipsBlurAndKeepsOriginalSpinnerDeadline() {
        reveal.setLoading(true, false);
        idle(150);
        assertFalse(image.hasLoadingBlur());
        reveal.setLoading(true, true);
        assertTrue(image.hasLoadingBlur());
        idle(80);
        assertEquals(View.VISIBLE, progress.getVisibility());
        reveal.cancelAndSettle();
        assertFalse(image.hasLoadingBlur());
    }

    @Test public void textureVideoKeepsBlurredPosterUntilFrameAndReadyThenRetainsFrameOnRebuffer() {
        setMotion(false);
        BunkrGalleryPagerAdapter adapter = adapter(NativeContentItem.KIND_MEDIA);
        BunkrGalleryPagerAdapter.Holder holder = holder(adapter);
        holder.image.setImageDrawable(bitmap());
        activity.setContentView(holder.itemView); // View-posted buffering delay needs an attached holder.
        assertTrue(holder.playerView.getVideoSurfaceView() instanceof TextureView);
        Player player = new IdlePlayer();
        adapter.activateVideo(0, player);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertTrue(holder.image.hasLoadingBlur());
        adapter.onVideoFirstFrame(0, player);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertEquals(View.VISIBLE, holder.image.getVisibility());
        adapter.onVideoBuffering(0, player, false);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertEquals(View.GONE, holder.image.getVisibility());
        assertFalse(holder.image.hasLoadingBlur());
        adapter.onVideoBuffering(0, player, true);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        idle(300);
        assertEquals(View.GONE, holder.image.getVisibility());
        assertEquals(View.VISIBLE, holder.progress.getVisibility());
        adapter.onViewRecycled(holder);
        player.release();
    }

    private void assertFitted(ZoomableImageView view, Drawable drawable,
                              float left, float top, float right, float bottom) {
        android.graphics.RectF bounds = new android.graphics.RectF(0, 0,
                drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        view.getImageMatrix().mapRect(bounds);
        assertEquals(left, bounds.left, 1f);
        assertEquals(top, bounds.top, 1f);
        assertEquals(right, bounds.right, 1f);
        assertEquals(bottom, bounds.bottom, 1f);
    }

    private BunkrGalleryPagerAdapter adapter(String kind) {
        BunkrGalleryPagerAdapter adapter = new BunkrGalleryPagerAdapter(activity,
                new BunkrGalleryPagerAdapter.Listener() {
                    @Override public void onMediaTap(int p, NativeContentItem item) { }
                    @Override public void onMediaLongPress(int p, NativeContentItem item) { }
                    @Override public void onResolvedImageFailed(int p, NativeContentItem item) { }
                });
        adapter.replace(Collections.singletonList(new NativeContentItem(kind, "Test", "test",
                "", "", "", "", "")), Collections.emptyMap());
        return adapter;
    }

    private BunkrGalleryPagerAdapter.Holder holder(BunkrGalleryPagerAdapter adapter) {
        BunkrGalleryPagerAdapter.Holder holder = adapter.onCreateViewHolder(new FrameLayout(activity), 0);
        adapter.onBindViewHolder(holder, 0);
        return holder;
    }

    private Drawable bitmap() {
        return new BitmapDrawable(activity.getResources(), Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888));
    }

    private Transition.ViewAdapter target() {
        return new Transition.ViewAdapter() {
            @Override public View getView() { return image; }
            @Override public Drawable getCurrentDrawable() { return image.getDrawable(); }
            @Override public void setDrawable(Drawable drawable) { image.setImageDrawable(drawable); }
        };
    }

    private void idle(long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private void setMotion(boolean enabled) {
        activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("immersive_motion_enabled", enabled).commit();
    }

    private static final class IdlePlayer extends SimpleBasePlayer {
        IdlePlayer() { super(Looper.getMainLooper()); }
        @Override protected State getState() {
            return new State.Builder().setAvailableCommands(new Player.Commands.Builder().build()).build();
        }
    }
}
