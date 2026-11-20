package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Paints above the video without taking focus or participating in touch handling. */
final class VideoSeekFeedback extends View {
    private static final long DURATION_MS = 780L;
    private static final float RIPPLE_END = .55f;
    private static final float FADE_START = .72f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final ValueAnimator animation = ValueAnimator.ofFloat(0f, 1f);
    private final Runnable clear = this::hide;
    private String label = "";
    private boolean forward;
    private float centerX;
    private float centerY;
    private float progress;

    VideoSeekFeedback(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(INVISIBLE);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);
        paint.setTextAlign(Paint.Align.CENTER);
        animation.setInterpolator(new LinearInterpolator());
        animation.setDuration(DURATION_MS);
        animation.addUpdateListener(animator -> {
            progress = (float) animator.getAnimatedValue();
            invalidate();
        });
    }

    void show(boolean forward, int seconds, float x, float y) {
        animation.cancel();
        removeCallbacks(clear);
        this.forward = forward;
        centerX = x;
        centerY = y;
        label = getResources().getString(forward ? R.string.video_seek_forward : R.string.video_seek_rewind,
                seconds);
        boolean animate = ZeroChillMotion.animationsEnabled(getContext());
        progress = animate ? 0f : RIPPLE_END;
        setVisibility(VISIBLE);
        invalidate();
        if (animate) animation.start();
        postDelayed(clear, DURATION_MS);
    }

    void hide() {
        animation.cancel();
        removeCallbacks(clear);
        setVisibility(INVISIBLE);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int accent = AppearanceTheme.accent(getContext());
        float half = getWidth() / 2f;
        float left = forward ? half : 0f;
        float right = forward ? getWidth() : half;
        canvas.save();
        canvas.clipRect(left, 0f, right, getHeight());

        if (progress < RIPPLE_END) {
            float elapsed = progress / RIPPLE_END;
            float remaining = 1f - elapsed;
            float expansion = 1f - remaining * remaining;
            float radius = 12f * density + Math.max(half, getHeight() / 2f) * expansion;
            paint.setColor(accent);
            paint.setAlpha(Math.round(60f * remaining));
            canvas.drawCircle(centerX, centerY, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            paint.setAlpha(Math.round(150f * remaining));
            canvas.drawCircle(centerX, centerY, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float opacity = Math.min(1f, (1f - progress) / (1f - FADE_START));
        float x = (left + right) / 2f;
        float y = getHeight() / 2f;
        float textWidth = paint.measureText(label);
        float width = Math.min(half, textWidth + 24f * density);
        float height = Math.max(18f * density, (paint.descent() - paint.ascent()) / 2f + 5f * density);
        paint.setColor(Color.BLACK);
        paint.setAlpha(Math.round(205f * opacity));
        canvas.drawRoundRect(x - width / 2f, y - height, x + width / 2f, y + height,
                height, height, paint);
        paint.setColor(accent);
        paint.setAlpha(Math.round(255f * opacity));
        paint.setTextScaleX(Math.min(1f, width / textWidth));
        canvas.drawText(label, x, y - (paint.ascent() + paint.descent()) / 2f, paint);
        paint.setTextScaleX(1f);
        canvas.restore();
    }
}
