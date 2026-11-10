package com.webapp.crazyshit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Finite circular reveal of the real gallery tree, independent of its loading. */
final class CreatorGalleryReveal extends FrameLayout {
    static final long DURATION_MS = 650L;
    static final long FALLBACK_MS = DURATION_MS + 400L;
    private final OnlyFapHeroRippleView ripple;
    private Animator animator;
    private boolean pending;
    private final Runnable fallback = this::cancelAndShow;
    private final ViewTreeObserver.OnPreDrawListener firstFrame = this::revealWhenLaidOut;

    CreatorGalleryReveal(Context context, View content, boolean animate) {
        super(context);
        setBackgroundColor(Color.BLACK);
        addView(content, new LayoutParams(-1, -1));
        ripple = new OnlyFapHeroRippleView(context);
        ripple.setAccent(AppearanceTheme.accent(context));
        addView(ripple, new LayoutParams(-1, -1));
        pending = animate && ZeroChillMotion.animationsEnabled(context);
        // Hide the entire tree before its first draw, including black background and
        // toolbar. A translucent window keeps the actual paused OnlyFap page behind it.
        setVisibility(pending ? INVISIBLE : VISIBLE);
    }

    private boolean revealWhenLaidOut() {
        if (!pending || getWidth() <= 0 || getHeight() <= 0) return true;
        removeFirstFrameListener();
        pending = false;
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            cancelAndShow();
            return true;
        }
        setVisibility(VISIBLE);
        int centerX = Math.round(getWidth() * .5f);
        int centerY = Math.round(getHeight() * .47f);
        float radius = coverRadius(getWidth(), getHeight(), centerX, centerY);
        try {
            Animator reveal = ViewAnimationUtils.createCircularReveal(this, centerX, centerY, 0f, radius);
            animator = reveal;
            reveal.setDuration(DURATION_MS);
            reveal.setInterpolator(new DecelerateInterpolator());
            reveal.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (animator == animation) {
                        animator = null;
                        removeCallbacks(fallback);
                        ripple.reset();
                        setVisibility(VISIBLE);
                    }
                }
            });
            ripple.reveal();
            reveal.start();
        } catch (RuntimeException unsupportedReveal) {
            cancelAndShow();
        }
        return true;
    }

    static float coverRadius(int width, int height, int centerX, int centerY) {
        return (float) Math.hypot(Math.max(centerX, width - centerX),
                Math.max(centerY, height - centerY));
    }

    /** Back, stop, detach, configuration change and the deadline all settle safely. */
    void cancelAndShow() {
        pending = false;
        removeFirstFrameListener();
        removeCallbacks(fallback);
        Animator previous = animator;
        animator = null;
        if (previous != null) {
            previous.removeAllListeners();
            previous.cancel();
        }
        ripple.reset();
        setVisibility(VISIBLE);
    }

    private void removeFirstFrameListener() {
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(firstFrame);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pending) {
            getViewTreeObserver().addOnPreDrawListener(firstFrame);
            postDelayed(fallback, FALLBACK_MS);
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Safe insets belong to the live gallery shell. Keep the decorative ring in
        // full-root coordinates so its center/radius match the native reveal clip.
        ripple.layout(0, 0, right - left, bottom - top);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (oldWidth > 0 && oldHeight > 0 && (width != oldWidth || height != oldHeight)) {
            // Insets, multi-window or a display resize invalidate the native clip's
            // fixed center/radius. Settle rather than leaving a clipped gallery edge.
            cancelAndShow();
        }
    }

    @Override protected void onDetachedFromWindow() {
        cancelAndShow();
        super.onDetachedFromWindow();
    }
}
