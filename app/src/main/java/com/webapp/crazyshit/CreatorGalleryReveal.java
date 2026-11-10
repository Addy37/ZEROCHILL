package com.webapp.crazyshit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PointF;
import android.view.View;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Finite circular reveal of the real gallery tree, independent of its loading. */
final class CreatorGalleryReveal extends FrameLayout {
    static final long DURATION_MS = 650L;
    static final long CLOSE_DURATION_MS = 550L;
    static final long FALLBACK_MS = DURATION_MS + 400L;
    private final OnlyFapHeroRippleView ripple;
    private final Intent launchIntent;
    private Animator animator;
    private boolean pending;
    private boolean closing;
    private boolean closed;
    private Runnable closeCompletion;
    private final Runnable fallback = this::cancelAndShow;
    private final ViewTreeObserver.OnPreDrawListener firstFrame = this::revealWhenLaidOut;

    CreatorGalleryReveal(Context context, View content, boolean animate) {
        super(context);
        launchIntent = context instanceof Activity ? ((Activity) context).getIntent() : null;
        setBackgroundColor(Color.BLACK);
        addView(content, new LayoutParams(-1, -1));
        ripple = new OnlyFapHeroRippleView(context);
        ripple.setAccent(AppearanceTheme.accent(context));
        addView(ripple, new LayoutParams(-1, -1));
        pending = animate && ZeroChillMotion.animationsEnabled(context);
        setVisibility(pending ? INVISIBLE : VISIBLE);
    }

    private PointF origin() { return CreatorGalleryTransition.resolveOrigin(this, launchIntent); }

    private boolean revealWhenLaidOut() {
        if (!pending || getWidth() <= 0 || getHeight() <= 0) return true;
        removeFirstFrameListener();
        pending = false;
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            cancelAndShow();
            return true;
        }
        setVisibility(VISIBLE);
        PointF point = origin();
        int centerX = Math.round(point.x), centerY = Math.round(point.y);
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
            ripple.setCenter(centerX, centerY);
            ripple.reveal();
            removeCallbacks(fallback);
            postDelayed(fallback, animationDeadline(getContext(), DURATION_MS));
            reveal.start();
        } catch (RuntimeException unsupportedReveal) {
            cancelAndShow();
        }
        return true;
    }

    /** True owns finishing, including repeated Back and lifecycle interruptions. */
    boolean close(Runnable finish) {
        if (closing || closed) return true;
        if (android.os.Build.VERSION.SDK_INT < 27 || !isAttachedToWindow()
                || getWidth() <= 0 || getHeight() <= 0
                || !ZeroChillMotion.animationsEnabled(getContext())) {
            cancelAndShow();
            return false;
        }
        PointF point = origin();
        int centerX = Math.round(point.x), centerY = Math.round(point.y);
        float radius = coverRadius(getWidth(), getHeight(), centerX, centerY);
        // Rings and native reveal share easing/duration. Reading the ring's actual
        // animated progress also honors Android's animator duration scale.
        float fraction = pending ? 0f : animator == null ? 1f : ripple.progress();
        float fromRadius = radius * Math.max(0f, Math.min(1f, fraction));
        stopAnimator();
        pending = false;
        removeFirstFrameListener();
        removeCallbacks(fallback);
        closing = true;
        cancelPendingInputEvents();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        closeCompletion = finish;
        if (fromRadius < .5f) {
            finishClose();
            return true;
        }
        long duration = Math.max(1L, Math.round(CLOSE_DURATION_MS * fraction));
        try {
            Animator reverse = ViewAnimationUtils.createCircularReveal(this,
                    centerX, centerY, fromRadius, 0f);
            animator = reverse;
            reverse.setDuration(duration);
            reverse.setInterpolator(new AccelerateInterpolator());
            reverse.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (animator == animation) finishClose();
                }
            });
            ripple.setCenter(centerX, centerY);
            ripple.reverse(fraction, duration);
            postDelayed(fallback, animationDeadline(getContext(), duration));
            reverse.start();
            return true;
        } catch (RuntimeException unsupportedReveal) {
            // The caller owns the immediate fade fallback; never call finish twice.
            closing = false;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            closeCompletion = null;
            cancelAndShow();
            return false;
        }
    }

    private void finishClose() {
        if (closed) return;
        closed = true;
        closing = false;
        pending = false;
        removeFirstFrameListener();
        removeCallbacks(fallback);
        stopAnimator();
        ripple.reset();
        // Cancelling a native clip restores normal drawing. Hide the live tree before
        // finishing so its last unclipped frame cannot flash across the source page.
        setVisibility(INVISIBLE);
        Runnable finish = closeCompletion;
        closeCompletion = null;
        if (finish != null) finish.run();
    }

    static long animationDeadline(Context context, long duration) {
        float scale = 1f;
        try {
            scale = android.provider.Settings.Global.getFloat(context.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (RuntimeException unavailableSetting) {
            // Keep a finite fail-safe when the platform setting cannot be read.
        }
        if (!Float.isFinite(scale) || scale <= 0f) scale = 1f;
        return Math.round(duration * (double) scale) + 400L;
    }

    static float coverRadius(int width, int height, int centerX, int centerY) {
        return (float) Math.hypot(Math.max(centerX, width - centerX),
                Math.max(centerY, height - centerY));
    }

    /** Stops opening, or completes an interrupted close once without restoring its tree. */
    void cancelAndShow() {
        if (closing) { finishClose(); return; }
        if (closed) { setVisibility(INVISIBLE); return; }
        pending = false;
        removeFirstFrameListener();
        removeCallbacks(fallback);
        stopAnimator();
        ripple.reset();
        setVisibility(VISIBLE);
    }

    private void stopAnimator() {
        Animator previous = animator;
        animator = null;
        if (previous != null) {
            previous.removeAllListeners();
            previous.cancel();
        }
    }

    private void removeFirstFrameListener() {
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(firstFrame);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return closing || closed || super.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return closing || closed || super.dispatchGenericMotionEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if ((closing || closed) && event.getKeyCode() != KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(event);
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
        // Only the live shell receives safe insets; rings share native clip coordinates.
        ripple.layout(0, 0, right - left, bottom - top);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (oldWidth > 0 && oldHeight > 0 && (width != oldWidth || height != oldHeight)) cancelAndShow();
    }

    @Override protected void onDetachedFromWindow() {
        cancelAndShow();
        super.onDetachedFromWindow();
    }
}
