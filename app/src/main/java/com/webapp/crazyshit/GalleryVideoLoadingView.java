package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Three small loading dots. Owns its delay, animation, and visibility lifecycle. */
final class GalleryVideoLoadingView extends View {
    static final long SHOW_DELAY_MS = 200L;
    private static final long FADE_MS = 100L;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable showPending = this::showIfWaiting;
    private final float density;
    private boolean waiting;
    private long showAt;
    private float phase;
    private ValueAnimator bounce;

    GalleryVideoLoadingView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        paint.setColor(UiPalette.PRIMARY);
        setContentDescription("Loading video");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        setVisibility(GONE);
    }

    void setLoading(boolean loading) {
        if (waiting == loading) {
            if (loading && getVisibility() == VISIBLE) updateAnimation();
            return;
        }
        waiting = loading;
        removeCallbacks(showPending);
        animate().cancel();
        animate().withEndAction(null);
        if (loading) {
            showAt = SystemClock.uptimeMillis() + SHOW_DELAY_MS;
            if (getVisibility() == VISIBLE) {
                setAlpha(1f);
                updateAnimation();
            } else scheduleShow();
        } else if (getVisibility() == VISIBLE && ZeroChillMotion.animationsEnabled(getContext())
                && isAttachedToWindow() && getWindowVisibility() == VISIBLE) {
            animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
                if (!waiting) hideImmediately();
            }).start();
        } else hideImmediately();
    }

    void cancelAndHide() {
        waiting = false;
        hideImmediately();
    }

    private void scheduleShow() {
        removeCallbacks(showPending);
        if (waiting && isAttachedToWindow() && getWindowVisibility() == VISIBLE) {
            postDelayed(showPending, Math.max(0L, showAt - SystemClock.uptimeMillis()));
        }
    }

    private void showIfWaiting() {
        if (!waiting || !isAttachedToWindow() || getWindowVisibility() != VISIBLE) return;
        // Don't animate attached pages whose parent has been hidden.
        if (getParent() instanceof View && !((View) getParent()).isShown()) return;
        setAlpha(1f);
        setVisibility(VISIBLE);
        updateAnimation();
    }

    private void updateAnimation() {
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            stopAnimation();
            return;
        }
        if (bounce != null || !isShown() || !isAttachedToWindow()) return;
        bounce = ValueAnimator.ofFloat(0f, 1f);
        bounce.setDuration(900L);
        bounce.setRepeatCount(ValueAnimator.INFINITE);
        bounce.setInterpolator(new LinearInterpolator());
        bounce.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        bounce.start();
    }

    private void stopAnimation() {
        if (bounce != null) bounce.cancel();
        bounce = null;
        phase = 0f;
        invalidate();
    }

    private void hideImmediately() {
        removeCallbacks(showPending);
        animate().cancel();
        animate().withEndAction(null);
        stopAnimation();
        setVisibility(GONE);
        setAlpha(1f);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleShow();
    }

    @Override protected void onDetachedFromWindow() {
        cancelAndHide();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (showPending == null) return; // View construction can dispatch this callback.
        if (visibility == VISIBLE) scheduleShow();
        else hideImmediately();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (showPending == null || changedView == this) return;
        if (visibility == VISIBLE) scheduleShow();
        else hideImmediately();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int dot = 0; dot < 3; dot++) {
            float local = (phase - dot * 0.16f + 1f) % 1f;
            float hop = local < 0.45f ? (float) Math.sin(local / 0.45f * Math.PI) : 0f;
            paint.setAlpha(Math.round(255f * (0.60f + 0.40f * hop)));
            canvas.drawCircle(getWidth() / 2f + (dot - 1) * 12f * density,
                    getHeight() / 2f + 2f * density - hop * 5f * density,
                    3f * density, paint);
        }
    }
}
