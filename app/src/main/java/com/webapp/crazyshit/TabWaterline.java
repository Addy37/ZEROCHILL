package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/** Traces a shallow moving boundary between two live primary pages. */
final class TabWaterline {
    private static final float[] GLINT_STOPS = {0f, .25f, .54f, .76f, 1f};
    private final Path reveal = new Path();
    private final Path edge = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int[] glintColors;
    private int shape;
    private float progress, originX;
    private float glintWidth, glintHeight;

    TabWaterline(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        int accent = AppearanceTheme.accent(context);
        int glint = Color.rgb((Color.red(accent) + 255) / 2,
                (Color.green(accent) + 255) / 2, (Color.blue(accent) + 255) / 2);
        glintColors = new int[]{accent & 0x00ffffff, withAlpha(accent, 42),
                withAlpha(glint, 105), withAlpha(accent, 47), accent & 0x00ffffff};
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f * density);
    }

    boolean update(int shape, float progress, float originX) {
        if (this.shape == shape && (shape == 0 || (this.progress == progress
                && this.originX == originX))) return false;
        this.shape = shape;
        this.progress = progress;
        this.originX = originX;
        return true;
    }

    boolean isFullyOpen() { return shape == 0 || progress >= 1f; }

    boolean isClosed() { return progress <= 0f; }

    void clipPage(Canvas canvas, float width, float height) {
        edge.rewind();
        float tension = (float) Math.sin(progress * Math.PI);
        if (shape == PrimaryTabReveal.BOTTOM) {
            float front = height * (1f - progress);
            float lift = Math.min(28f * density, height * .075f) * tension;
            float reach = Math.max(width * .14f, Math.min(width * .86f, originX));
            float crest = reach + (width * .5f - reach) * (.55f * progress);
            float shoulder = front + lift * .23f;
            float peak = front - lift;
            edge.moveTo(0f, shoulder);
            edge.cubicTo(crest * .40f, shoulder, crest * .60f, peak, crest, peak);
            edge.cubicTo(crest + (width - crest) * .40f, peak,
                    crest + (width - crest) * .62f, shoulder, width, shoulder);
            reveal.set(edge);
            reveal.lineTo(width, height);
            reveal.lineTo(0f, height);
        } else {
            boolean right = shape == PrimaryTabReveal.FROM_RIGHT;
            float sign = right ? 1f : -1f;
            float front = width * (right ? 1f - progress : progress);
            float sweep = Math.min(19f * density, width * .066f) * tension * sign;
            edge.moveTo(front + sweep * .45f, 0f);
            edge.cubicTo(front - sweep * 1.9f, height * .25f,
                    front - sweep * .67f, height * .56f, front + sweep * .42f, height);
            reveal.set(edge);
            float wall = right ? width : 0f;
            reveal.lineTo(wall, height);
            reveal.lineTo(wall, 0f);
        }
        reveal.close();
        canvas.clipPath(reveal);
    }

    void drawHighlight(Canvas canvas, float width, float height) {
        float dx = shape == PrimaryTabReveal.BOTTOM ? width : 0f;
        float dy = shape == PrimaryTabReveal.BOTTOM ? 0f : height;
        if (glintWidth != dx || glintHeight != dy) {
            glintWidth = dx;
            glintHeight = dy;
            paint.setShader(new LinearGradient(0f, 0f, dx, dy, glintColors, GLINT_STOPS,
                    Shader.TileMode.CLAMP));
        }
        float envelope = Math.min(1f, Math.min(progress * 5f, (1f - progress) * 5f));
        paint.setAlpha(Math.round(255f * envelope));
        canvas.drawPath(edge, paint);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }
}
