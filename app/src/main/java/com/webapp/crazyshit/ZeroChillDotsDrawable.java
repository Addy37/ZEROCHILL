package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Animatable;
import android.os.SystemClock;

/** Shared lightweight three-dot loading mark. */
final class ZeroChillDotsDrawable extends Drawable implements Animatable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private int alpha = 255;
        private final Context context;
        private boolean running;
        private int phase;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                phase = (phase + 1) % 3;
                invalidateSelf();
                if (running) scheduleSelf(this, SystemClock.uptimeMillis() + 280);
            }
        };
        ZeroChillDotsDrawable(Context context) {
            this.context = context;
            density = context.getResources().getDisplayMetrics().density;
            paint.setColor(ZeroChillUi.color(context, R.color.zc_cyan));
        }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float radius = Math.min(3.5f * density, bounds.width() / 10f);
            float spacing = Math.min(11f * density, bounds.width() / 4f);
            float center = bounds.exactCenterX();
            float y = bounds.exactCenterY();
            for (int i = -1; i <= 1; i++) {
                paint.setAlpha(i + 1 == phase ? alpha : Math.round(alpha * 0.45f));
                canvas.drawCircle(center + i * spacing, y, radius, paint);
            }
        }
        @Override public void start() {
            if (running || !ZeroChillMotion.animationsEnabled(context)) return;
            running = true;
            scheduleSelf(tick, SystemClock.uptimeMillis() + 280);
        }
        @Override public void stop() { running = false; unscheduleSelf(tick); }
        @Override public boolean isRunning() { return running; }
        @Override public boolean setVisible(boolean visible, boolean restart) {
            if (visible) start(); else stop();
            return super.setVisible(visible, restart);
        }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
