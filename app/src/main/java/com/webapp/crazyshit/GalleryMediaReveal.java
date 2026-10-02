package com.webapp.crazyshit;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.request.transition.DrawableCrossFadeTransition;
import com.bumptech.glide.request.transition.NoTransition;
import com.bumptech.glide.request.transition.Transition;

/** Loading motion for one pager holder, independent of Activity shared-element motion. */
final class GalleryMediaReveal {
    static final long SPINNER_DELAY_MS = 220L;
    static final int IMAGE_FADE_MS = 190;
    static final long POSTER_FADE_MS = 150L;

    private final ZoomableImageView image;
    private final View progress;
    private boolean waiting;
    private boolean videoRevealed;
    private final Runnable showProgress;

    GalleryMediaReveal(ZoomableImageView image, View progress) {
        this.image = image;
        this.progress = progress;
        showProgress = () -> {
            if (waiting) progress.setVisibility(View.VISIBLE);
        };
    }

    void setLoading(boolean loading) {
        if (waiting == loading) return;
        waiting = loading;
        progress.removeCallbacks(showProgress);
        if (loading) progress.postDelayed(showProgress, SPINNER_DELAY_MS);
        else progress.setVisibility(View.GONE);
    }

    Transition<Drawable> imageTransition(DataSource source, boolean allowMotion) {
        // Cache hits display immediately. Never animate the shared element during expansion.
        return source == DataSource.REMOTE && allowMotion
                && ZeroChillMotion.animationsEnabled(image.getContext())
                ? new DrawableCrossFadeTransition(IMAGE_FADE_MS, true)
                : NoTransition.get();
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
            image.setAlpha(1f);
            return;
        }
        image.animate().alpha(0f).setDuration(POSTER_FADE_MS).withEndAction(() -> {
            if (!videoRevealed) return;
            image.setVisibility(View.GONE);
            image.setAlpha(1f);
        }).start();
    }

    void cancelAndSettle() {
        waiting = false;
        progress.removeCallbacks(showProgress);
        progress.setVisibility(View.GONE);
        image.animate().cancel();
        image.animate().withEndAction(null);
        image.setAlpha(1f);
        if (videoRevealed) image.setVisibility(View.GONE);
        Drawable drawable = image.getDrawable();
        if (drawable instanceof TransitionDrawable) {
            TransitionDrawable transition = (TransitionDrawable) drawable;
            image.setImageDrawable(transition.getDrawable(transition.getNumberOfLayers() - 1));
        }
    }
}
