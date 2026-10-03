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
    private org.robolectric.android.controller.ActivityController<Activity> activityController;
    private ZoomableImageView image;
    private GalleryVideoLoadingView dots;
    private GalleryMediaReveal reveal;

    @Before public void setUp() {
        activityController = Robolectric.buildActivity(Activity.class).setup();
        activity = activityController.get();
        setMotion(true);
        FrameLayout root = new FrameLayout(activity);
        image = new ZoomableImageView(activity);
        root.addView(image);
        dots = new GalleryVideoLoadingView(activity);
        root.addView(dots, new FrameLayout.LayoutParams(60, 32));
        attachContent(root);
        reveal = new GalleryMediaReveal(image, dots);
    }

    @After public void tearDown() {
        reveal.cancelAndSettle();
        setMotion(true);
        activity.finish();
    }

    @Test public void loadingKeepsPreviewBlurredUntilCompletion() {
        reveal.setLoading(true);
        idle(5000);
        assertTrue(image.hasLoadingBlur());
        reveal.setLoading(false);
        assertFalse(image.hasLoadingBlur());
        idle(5000);
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
            Drawable full = bitmap();
            assertTrue(reveal.imageTransition(source, true).transition(full, target()));
            assertSame(full, image.getDrawable());
        }
        assertTrue(reveal.imageTransition(DataSource.REMOTE, false).transition(bitmap(), target()));
        assertFalse(image.getDrawable() instanceof TransitionDrawable);
        setMotion(false);
        assertTrue(reveal.imageTransition(DataSource.REMOTE, true).transition(bitmap(), target()));
        assertFalse(image.getDrawable() instanceof TransitionDrawable);
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

    @Test public void cancelledLoadingRemovesBlurOnRecycledOrExitedMedia() {
        reveal.setLoading(true);
        idle(100);
        reveal.cancelAndSettle();
        idle(400);
        assertFalse(image.hasLoadingBlur());
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
        assertNoSpinner(holder);
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

    @Test public void animatedResourceCallbacksReachViewAndDrawingRestoresResourceState() {
        image.layout(0, 0, 720, 1280);
        Drawable full = bitmap();
        image.setImageDrawable(bitmap());
        reveal.imageTransition(DataSource.REMOTE, true).transition(full, target());
        Drawable fade = image.getDrawable();
        int[] forwarded = new int[3];
        Runnable frame = () -> { };
        fade.setCallback(new Drawable.Callback() {
            @Override public void invalidateDrawable(Drawable who) { forwarded[0]++; }
            @Override public void scheduleDrawable(Drawable who, Runnable action, long when) {
                assertSame(frame, action);
                forwarded[1]++;
            }
            @Override public void unscheduleDrawable(Drawable who, Runnable action) {
                assertSame(frame, action);
                forwarded[2]++;
            }
        });
        full.invalidateSelf();
        full.scheduleSelf(frame, 100L);
        full.unscheduleSelf(frame);
        assertArrayEquals(new int[]{1, 1, 1}, forwarded);
        Drawable.Callback callback = full.getCallback();
        android.graphics.Rect bounds = new android.graphics.Rect(full.getBounds());
        int alpha = full.getAlpha();
        fade.draw(new android.graphics.Canvas(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)));
        assertSame(callback, full.getCallback());
        assertEquals(bounds, full.getBounds());
        assertEquals(alpha, full.getAlpha());
        reveal.cancelAndSettle();
        assertSame(full, image.getDrawable());
        assertSame(image, full.getCallback());
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

    @Test public void sharedExpansionKeepsBlurEvenWhenResolutionHasFinished() {
        BunkrGalleryPagerAdapter adapter = adapter(NativeContentItem.KIND_IMAGE);
        adapter.replace(Collections.singletonList(new NativeContentItem(NativeContentItem.KIND_IMAGE,
                "Test", "test", "thumbnail", "", "", "", "")), Collections.emptyMap());
        adapter.setInitialSharedElement("test", "zerochill_gallery_media_test");
        adapter.setResolvedUrl(0, "full");
        BunkrGalleryPagerAdapter.Holder holder = holder(adapter);
        assertTrue(holder.image.hasLoadingBlur());
        holder.imageLoading = false; // Cached thumbnail has arrived while full image is deferred.
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertTrue(holder.image.hasLoadingBlur());
        assertNoSpinner(holder);
        adapter.finishOpeningTransition();
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertTrue(holder.image.hasLoadingBlur()); // Full-request rebind has not run yet.
        adapter.onViewRecycled(holder);
    }

    @Test public void readyFullImageIsNotReblurredBySharedExpansion() {
        BunkrGalleryPagerAdapter adapter = adapter(NativeContentItem.KIND_IMAGE);
        adapter.setInitialSharedElement("test", "zerochill_gallery_media_test");
        adapter.setResolvedUrl(0, "full");
        BunkrGalleryPagerAdapter.Holder holder = holder(adapter);
        holder.imageLoading = false;
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertFalse(holder.image.hasLoadingBlur());
        assertNoSpinner(holder);
        adapter.onViewRecycled(holder);
    }

    @Test public void thumbnailDeliveryNeverRemovesLoadingBlur() {
        image.setImageDrawable(bitmap());
        reveal.setLoading(true);
        assertFalse(reveal.imageTransition(DataSource.MEMORY_CACHE, true, false)
                .transition(bitmap(), target()));
        assertTrue(image.hasLoadingBlur());
    }

    @Test public void sharpResourceIsInstalledBeforeBlurIsRemoved() {
        image.layout(0, 0, 720, 1280);
        image.setImageDrawable(bitmap());
        reveal.setLoading(true);
        Drawable full = bitmap();
        Transition.ViewAdapter checked = new Transition.ViewAdapter() {
            @Override public View getView() { return image; }
            @Override public Drawable getCurrentDrawable() { return image.getDrawable(); }
            @Override public void setDrawable(Drawable drawable) {
                assertTrue(image.hasLoadingBlur());
                image.setImageDrawable(drawable);
            }
        };
        assertTrue(reveal.imageTransition(DataSource.MEMORY_CACHE, true).transition(full, checked));
        assertSame(full, image.getDrawable());
        assertFalse(image.hasLoadingBlur());
        reveal.setLoading(true);
        assertTrue(reveal.imageTransition(DataSource.REMOTE, true).transition(bitmap(), checked));
        assertTrue(image.getDrawable() instanceof GalleryFitCrossFade);
        assertFalse(image.hasLoadingBlur());
    }

    @Test public void textureVideoKeepsBlurredPosterUntilFrameAndReadyThenRetainsFrameOnRebuffer() {
        setMotion(false);
        BunkrGalleryPagerAdapter adapter = adapter(NativeContentItem.KIND_MEDIA);
        BunkrGalleryPagerAdapter.Holder holder = holder(adapter);
        holder.image.setImageDrawable(bitmap());
        attachContent(holder.itemView);
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
        assertNoSpinner(holder);
        adapter.onViewRecycled(holder);
        player.release();
    }

    @Test public void dotsUseOneDelayAndFastVideoLoadsNeverFlash() {
        reveal.setVideoLoading(true);
        idle(150);
        assertEquals(View.GONE, dots.getVisibility());
        reveal.setVideoLoading(true);
        idle(80);
        assertEquals("attached=" + dots.isAttachedToWindow() + " window=" + dots.getWindowVisibility()
                + " parentShown=" + ((View) dots.getParent()).isShown()
                + " waiting=" + ReflectionHelpers.getField(dots, "waiting"),
                View.VISIBLE, dots.getVisibility());
        assertNotNull(ReflectionHelpers.getField(dots, "bounce"));
        reveal.setVideoLoading(false);
        idle(250);
        assertEquals(View.GONE, dots.getVisibility());
        assertNull(ReflectionHelpers.getField(dots, "bounce"));
        reveal.setVideoLoading(true);
        idle(100);
        reveal.setVideoLoading(false);
        idle(1000);
        assertEquals(View.GONE, dots.getVisibility());
    }

    @Test public void dotsRemainStillWhenMotionIsDisabledAndStopDuringReturn() {
        setMotion(false);
        reveal.setVideoLoading(true);
        idle(300);
        assertEquals(View.VISIBLE, dots.getVisibility());
        assertEquals("Loading video", dots.getContentDescription());
        assertNull(ReflectionHelpers.getField(dots, "bounce"));
        reveal.cancelAndSettle();
        idle(1000);
        assertEquals(View.GONE, dots.getVisibility());
        reveal.setVideoLoading(true);
        idle(100);
        reveal.cancelAndSettle();
        idle(1000);
        assertEquals(View.GONE, dots.getVisibility());
    }

    @Test public void dotsRestartCleanlyIfBufferingReturnsDuringTheirFade() {
        reveal.setVideoLoading(true);
        idle(300);
        reveal.setVideoLoading(false);
        idle(30);
        reveal.setVideoLoading(true);
        idle(300);
        assertEquals(View.VISIBLE, dots.getVisibility());
        assertEquals(1f, dots.getAlpha(), 0f);
        assertNotNull(ReflectionHelpers.getField(dots, "bounce"));
    }

    @Test public void hiddenParentAndDetachStopDotsAndCancelPendingCallbacks() {
        FrameLayout root = (FrameLayout) dots.getParent();
        reveal.setVideoLoading(true);
        idle(300);
        root.setVisibility(View.GONE);
        idle(300);
        assertEquals(View.GONE, dots.getVisibility());
        assertNull(ReflectionHelpers.getField(dots, "bounce"));
        root.setVisibility(View.VISIBLE);
        idle(300);
        assertEquals(View.VISIBLE, dots.getVisibility());
        setWindowVisibility(root, View.GONE);
        idle(300);
        assertEquals(View.GONE, dots.getVisibility());
        assertNull(ReflectionHelpers.getField(dots, "bounce"));
        setWindowVisibility(root, View.VISIBLE);
        idle(300);
        assertEquals(View.VISIBLE, dots.getVisibility());
        root.removeView(dots);
        idle(500);
        assertEquals(View.GONE, dots.getVisibility());
        assertNull(ReflectionHelpers.getField(dots, "bounce"));
        root.addView(dots);
        idle(500);
        assertEquals(View.GONE, dots.getVisibility());
    }

    @Test public void resolvingAndBufferingUseDotsWhileManualVideoKeepsPlayButton() {
        setMotion(false);
        BunkrGalleryPagerAdapter adapter = adapter(NativeContentItem.KIND_MEDIA);
        BunkrGalleryPagerAdapter.Holder holder = holder(adapter);
        attachContent(holder.itemView);
        assertEquals(View.VISIBLE, holder.play.getVisibility());
        assertEquals(View.GONE, holder.loadingDots.getVisibility());
        adapter.setLoading(0, true);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertEquals(View.GONE, holder.play.getVisibility());
        idle(300);
        assertEquals(View.VISIBLE, holder.loadingDots.getVisibility());
        Player player = new IdlePlayer();
        adapter.activateVideo(0, player);
        adapter.setLoading(0, false);
        adapter.onVideoBuffering(0, player, false);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertEquals(View.VISIBLE, holder.loadingDots.getVisibility()); // READY without a frame.
        adapter.onVideoFirstFrame(0, player);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertEquals(View.GONE, holder.loadingDots.getVisibility());
        adapter.onVideoBuffering(0, player, true);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        idle(300);
        assertEquals(View.VISIBLE, holder.loadingDots.getVisibility());
        adapter.setFailed(0, true);
        adapter.clearActiveVideo();
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertEquals(View.GONE, holder.loadingDots.getVisibility());
        assertEquals(View.VISIBLE, holder.failure.getVisibility());
        adapter.onViewRecycled(holder);
        player.release();
    }

    @Test public void photosNeverShowVideoDotsAndRecyclingCancelsVideoDelay() {
        BunkrGalleryPagerAdapter adapter = adapter(NativeContentItem.KIND_IMAGE);
        BunkrGalleryPagerAdapter.Holder holder = holder(adapter);
        attachContent(holder.itemView);
        adapter.setLoading(0, true);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        idle(1000);
        assertEquals(View.GONE, holder.loadingDots.getVisibility());
        adapter.onViewRecycled(holder);
        adapter = adapter(NativeContentItem.KIND_MEDIA);
        holder = holder(adapter);
        attachContent(holder.itemView);
        adapter.setLoading(0, true);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        idle(100);
        adapter.onViewRecycled(holder);
        idle(1000);
        assertEquals(View.GONE, holder.loadingDots.getVisibility());
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test public void dotsRenderElectricBlueAtThreeBouncePhases() throws Exception {
        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round(60f * density), height = Math.round(32f * density);
        dots.layout(0, 0, width, height);
        Bitmap strip = Bitmap.createBitmap(width * 3, height, Bitmap.Config.ARGB_8888);
        strip.eraseColor(android.graphics.Color.BLACK);
        android.graphics.Canvas canvas = new android.graphics.Canvas(strip);
        float[] phases = {0f, 0.22f, 0.36f};
        for (int i = 0; i < phases.length; i++) {
            ReflectionHelpers.setField(dots, "phase", phases[i]);
            int save = canvas.save();
            canvas.translate(i * width, 0f);
            dots.draw(canvas);
            canvas.restoreToCount(save);
        }
        int bluePixels = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width * 3; x++) {
            int color = strip.getPixel(x, y);
            if (android.graphics.Color.blue(color) > 70
                    && android.graphics.Color.red(color) < 20) bluePixels++;
        }
        assertTrue(bluePixels > 10);
        java.io.File out = new java.io.File("build/reports/visual-tests/gallery-video-dots.png");
        out.getParentFile().mkdirs();
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(out)) {
            assertTrue(strip.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }

    private void attachContent(View content) {
        activity.setContentView(content);
        activityController.visible();
        // Robolectric attaches/shows content but leaves AttachInfo's window visibility GONE.
        // Supply the WindowManager visibility event so loading uses the same gate as a device.
        setWindowVisibility(activity.getWindow().getDecorView(), View.VISIBLE);
    }

    private void setWindowVisibility(View view, int visibility) {
        Object attachInfo = ReflectionHelpers.getField(view, "mAttachInfo");
        assertNotNull(attachInfo);
        ReflectionHelpers.setField(attachInfo, "mWindowVisibility", visibility);
        view.dispatchWindowVisibilityChanged(visibility);
    }

    private void assertNoSpinner(BunkrGalleryPagerAdapter.Holder holder) {
        FrameLayout root = (FrameLayout) holder.itemView;
        for (int i = 0; i < root.getChildCount(); i++) {
            assertFalse(root.getChildAt(i) instanceof ProgressBar);
        }
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
