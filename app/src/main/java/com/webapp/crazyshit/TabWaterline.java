package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.animation.AnimationUtils;

/** Clips the incoming page and redraws a narrow strip through the moving waterline. */
final class TabWaterline {
    private static final float HORIZONTAL_STRETCH = 1.32f;
    private static final long RIPPLE_DECAY_MS = 145L;
    private static final int[] GLINT_COLORS = {0x30ffffff, 0xf5ffffff, 0x60ffffff, 0xffffffff, 0x38ffffff};
    private static final float[] GLINT_STOPS = {0f, .26f, .47f, .68f, 1f};
    private final Path reveal = new Path();
    private final Path inner = new Path();
    private final Path edge = new Path();
    private final Path trailing = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int accent;
    private int shape;
    private float progress, originX, originY;
    private long changedAt;
    private float glintX, glintY;
    private Shader glint;

    TabWaterline(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        accent = AppearanceTheme.accent(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    boolean update(int shape, float progress, float originX, float originY) {
        if (this.shape == shape && (shape == 0 || (this.progress == progress
                && this.originX == originX && this.originY == originY))) return false;
        this.shape = shape;
        this.progress = progress;
        this.originX = originX;
        this.originY = originY;
        changedAt = AnimationUtils.currentAnimationTimeMillis();
        return true;
    }

    boolean isFullyOpen() { return shape == 0 || progress >= 1f; }

    boolean isClosed() { return progress <= 0f; }

    void clipPage(Canvas canvas, float width, float height) {
        reveal.rewind();
        edge.rewind();
        trace(reveal, edge, width, height, 0f);
        canvas.clipPath(reveal);
    }

    int clipRefraction(Canvas canvas, float width, float height) {
        inner.rewind();
        trace(inner, null, width, height, 9f * density);
        int save = canvas.save();
        if (!canvas.clipOutPath(inner)) {
            canvas.restoreToCount(save);
            return -1;
        }
        if (shape == PrimaryTabReveal.BOTTOM) {
            // Sample toward the dock so the strip magnifies around the curved edge.
            canvas.scale(1.026f, 1.026f, originX, originY);
        } else {
            float direction = shape == PrimaryTabReveal.FROM_RIGHT ? 1f : -1f;
            canvas.translate(direction * 3f * density, .6f * density);
        }
        return save;
    }

    boolean drawHighlight(Canvas canvas, float width, float height) {
        long elapsed = AnimationUtils.currentAnimationTimeMillis() - changedAt;
        float ripple = Math.max(0f, 1f - elapsed / (float) RIPPLE_DECAY_MS);
        float envelope = Math.min(1f, Math.min(progress * 9f, (1f - progress) * 7f));
        // The curtain keeps its specular edge while parked. Only the wake fades away.
        stroke(canvas, edge, accent, 5.2f, Math.round(105f * envelope));
        float dx = shape == PrimaryTabReveal.BOTTOM ? width : 0f;
        float dy = shape == PrimaryTabReveal.BOTTOM ? 0f : height;
        if (glint == null || glintX != dx || glintY != dy) {
            glintX = dx;
            glintY = dy;
            glint = new LinearGradient(0f, 0f, dx, dy, GLINT_COLORS, GLINT_STOPS, Shader.TileMode.CLAMP);
        }
        paint.setShader(glint);
        stroke(canvas, edge, Color.WHITE, 1.6f, Math.round(235f * envelope));
        if (ripple > 0f) {
            trailing.rewind();
            trace(null, trailing, width, height, (10f + 6f * (1f - ripple)) * density);
            stroke(canvas, trailing, Color.WHITE, 1.25f, Math.round(88f * ripple * envelope));
        }
        paint.setShader(null);
        return ripple > 0f;
    }

    private void stroke(Canvas canvas, Path path, int color, float widthDp, int opacity) {
        paint.setColor(color);
        paint.setStrokeWidth(widthDp * density);
        paint.setAlpha(opacity);
        canvas.drawPath(path, paint);
    }

    private void trace(Path region, Path line, float width, float height, float depth) {
        if (shape == PrimaryTabReveal.BOTTOM) {
            float dx = Math.max(Math.abs(originX), Math.abs(width - originX)) / HORIZONTAL_STRETCH;
            float dy = Math.max(Math.abs(originY), Math.abs(height - originY));
            float radius = (float) Math.hypot(dx, dy) * progress - depth;
            if (radius <= 0f) return;
            float rx = radius * HORIZONTAL_STRETCH;
            Path oval = region != null ? region : line;
            float crest = rx * .07f * (float) Math.sin(progress * Math.PI);
            float kx = .5523f * rx, ky = .5523f * radius;
            float x = originX, y = originY;
            oval.moveTo(x + crest, y - radius);
            oval.cubicTo(x + crest + kx, y - radius, x + rx, y - ky, x + rx, y);
            oval.cubicTo(x + rx, y + ky, x + kx, y + radius, x, y + radius);
            oval.cubicTo(x - kx, y + radius, x - rx, y + ky, x - rx, y);
            oval.cubicTo(x - rx, y - ky, x + crest - kx, y - radius, x + crest, y - radius);
            oval.close();
            if (region != null && line != null) line.set(region);
        } else {
            boolean right = shape == PrimaryTabReveal.FROM_RIGHT;
            float sign = right ? 1f : -1f;
            float position = width * (right ? 1f - progress : progress);
            float bow = Math.min(Math.min(position, width - position), .13f * width) * sign;
            float wall = right ? width + depth : -depth;
            float front = position + depth * sign;
            Path curve = region != null ? region : line;
            curve.moveTo(front + bow, 0f);
            curve.cubicTo(front - 1.9f * bow, height * .26f,
                    front - 2.1f * bow, height * .66f, front + bow, height);
            if (region != null) {
                if (line != null) line.set(region);
                region.lineTo(wall, height);
                region.lineTo(wall, 0f);
                region.close();
            }
        }
    }
}
