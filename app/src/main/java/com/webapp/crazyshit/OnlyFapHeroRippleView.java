package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Visual-only ripple on top of an already committed hero. Never owns media or Glide requests. */
final class OnlyFapHeroRippleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float progress = 1f;
    private float originX = Float.NaN;
    private float originY = Float.NaN;
    private int accent = Color.rgb(8, 146, 208);

    OnlyFapHeroRippleView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setAccent(int color) { accent = color; invalidate(); }

    void setCenter(float x, float y) {
        originX = x;
        originY = y;
        invalidate();
    }

    float progress() { return progress; }

    void reveal() { animateRings(0f, 1f, 650L); }

    void reverse(float fromFraction, long duration) {
        animateRings(Math.max(0f, Math.min(1f, fromFraction)), 0f, duration);
    }

    private void animateRings(float from, float to, long duration) {
        if (animator != null) animator.cancel();
        if (!isAttachedToWindow() || !ZeroChillMotion.animationsEnabled(getContext())) { progress = 1f; invalidate(); return; }
        progress = from;
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(duration);
        animator.setInterpolator(to < from ? new android.view.animation.AccelerateInterpolator()
                : new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    void reset() {
        if (animator != null) { animator.cancel(); animator = null; }
        progress = 1f;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress >= 1f) return;
        float centerX = Float.isFinite(originX) ? originX : getWidth() * .5f;
        float centerY = Float.isFinite(originY) ? originY : getHeight() * .47f;
        float radius = (float) Math.hypot(
                Math.max(centerX, getWidth() - centerX),
                Math.max(centerY, getHeight() - centerY));
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 2; i++) {
            float phase = Math.max(0f, Math.min(1f, (progress - i * .14f) / (1f - i * .14f)));
            if (phase <= 0f) continue;
            float opacity = (1f - phase) * (i == 0 ? 128f : 82f);
            if (i == 0) {
                paint.setColor(Color.argb(Math.round(opacity), 243, 249, 255));
                paint.setStrokeWidth(dp(2.5f));
            } else {
                paint.setColor(Color.argb(Math.round(opacity), Color.red(accent),
                        Color.green(accent), Color.blue(accent)));
                paint.setStrokeWidth(dp(7f));
            }
            canvas.drawCircle(centerX, centerY, radius * phase, paint);
        }
    }

    @Override protected void onDetachedFromWindow() {
        reset();
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return getResources().getDisplayMetrics().density * v;
    }
}
