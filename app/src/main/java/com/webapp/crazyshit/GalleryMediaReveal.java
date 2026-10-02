package com.webapp.crazyshit;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.request.transition.NoTransition;
import com.bumptech.glide.request.transition.Transition;

/** Loading motion for one pager holder, independent of Activity shared-element motion. */
final class GalleryMediaReveal {
    static final int IMAGE_FADE_MS = 190;
    static final long POSTER_FADE_MS = 150L;

    private final ZoomableImageView image;
    private boolean videoRevealed;

    GalleryMediaReveal(ZoomableImageView image) {
        this.image = image;
    }

    void setLoading(boolean loading) {
        // The preview itself carries loading. A revealed video retains its last frame.
        if (!videoRevealed) image.setLoadingBlur(loading);
    }

    Transition<Drawable> imageTransition(DataSource source, boolean allowMotion) {
        return imageTransition(source, allowMotion, true);
    }

    Transition<Drawable> imageTransition(DataSource source, boolean allowMotion, boolean fullImage) {
        if (!fullImage) return NoTransition.get();
        // Cache hits display immediately. Never animate the shared element during expansion.
        boolean animate = source == DataSource.REMOTE && allowMotion
                && ZeroChillMotion.animationsEnabled(image.getContext());
        return (resource, target) -> {
            Drawable preview = image.hasLoadingBlur() && animate
                    ? image.loadingPreviewSnapshot() : target.getCurrentDrawable();
            if (!animate || preview == null || resource.getIntrinsicWidth() <= 0
                    || resource.getIntrinsicHeight() <= 0) {
                // Install sharp pixels before removing blur, including cache/reduced-motion hits.
                target.setDrawable(resource);
                image.setLoadingBlur(false);
                return true;
            }
            GalleryFitCrossFade fade = new GalleryFitCrossFade(preview, resource,
                    image.getWidth(), image.getHeight());
            target.setDrawable(fade);
            image.setLoadingBlur(false);
            fade.startTransition(IMAGE_FADE_MS);
            return true;
        };
    }

    void showPoster() {
        videoRevealed = false;
        image.animate().cancel();
        image.animate().withEndAction(null);
        image.setAlpha(1f);
        image.setVisibility(View.VISIBLE);
    }

    void revealVideo(boolean allowMotion) {
        if (videoRevealed) return;
        videoRevealed = true;
        image.animate().cancel();
        image.animate().withEndAction(null);
        if (image.getDrawable() == null || !allowMotion
                || !ZeroChillMotion.animationsEnabled(image.getContext())) {
            image.setVisibility(View.GONE);
            image.setLoadingBlur(false);
            image.setAlpha(1f);
            return;
        }
        image.animate().alpha(0f).setDuration(POSTER_FADE_MS).withEndAction(() -> {
            if (!videoRevealed) return;
            image.setVisibility(View.GONE);
            image.setLoadingBlur(false);
            image.setAlpha(1f);
        }).start();
    }

    void cancelAndSettle() {
        image.animate().cancel();
        image.animate().withEndAction(null);
        image.setAlpha(1f);
        image.setLoadingBlur(false);
        if (videoRevealed) image.setVisibility(View.GONE);
        Drawable drawable = image.getDrawable();
        if (drawable instanceof TransitionDrawable) {
            TransitionDrawable transition = (TransitionDrawable) drawable;
            image.setImageDrawablePreservingZoom(transition.getDrawable(transition.getNumberOfLayers() - 1));
        }
    }

    boolean isVideoRevealed() { return videoRevealed; }
}
