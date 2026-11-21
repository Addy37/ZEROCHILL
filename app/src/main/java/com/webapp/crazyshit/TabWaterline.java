package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/**
 * Clips one live page behind a restrained, elastic liquid edge.
 * The underlying page renders once. No snapshots, shaders over video, or extra layers.
 */
final class TabWaterline {
    private static final int SEGMENTS = 16;
    private static final float[] GLINT_STOPS = {0f, .23f, .51f, .79f, 1f};
    private final Path reveal = new Path();
    private final Path edge = new Path();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Context context;
    private final float density;
    private int shape;
    private int accent = Color.TRANSPARENT;
    private int glintShape;
    private float progress, originX;
    private float glintWidth = -1f, glintHeight = -1f;

    TabWaterline(Context context) {
        this.context = context;
        density = context.getResources().getDisplayMetrics().density;
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(5f * density);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.1f * density);
    }

    boolean update(int shape, float progress, float originX) {
        float bounded = Math.max(0f, Math.min(1f, progress));
        if (this.shape == shape && (shape == 0 || (this.progress == bounded
                && this.originX == originX))) return false;
        this.shape = shape;
        this.progress = bounded;
        this.originX = originX;
        return true;
    }

    boolean isFullyOpen() { return shape == 0 || progress >= 1f; }

    boolean isClosed() { return progress <= 0f; }

    void clipPage(Canvas canvas, float width, float height) {
        edge.rewind();
        reveal.rewind();
        // The waterline relaxes at either end, leaving normal screen geometry at rest.
        float tension = (float) Math.sin(Math.PI * progress);
        if (shape == PrimaryTabReveal.BOTTOM) {
            traceBottom(width, height, tension);
            reveal.set(edge);
            reveal.lineTo(width, height);
            reveal.lineTo(0f, height);
        } else {
            boolean fromRight = shape == PrimaryTabReveal.FROM_RIGHT;
            traceSide(width, height, fromRight, tension);
            reveal.set(edge);
            float wall = fromRight ? width : 0f;
            reveal.lineTo(wall, height);
            reveal.lineTo(wall, 0f);
        }
        reveal.close();
        canvas.clipPath(reveal);
    }

    private void traceBottom(float width, float height, float tension) {
        // Keep most of the boundary flat. A small bead pulls toward the tapped dock slot,
        // followed by a shallower opposing wake. Unlike a wave/oval, it does not sweep
        // the whole screen or obscure content far from the active edge.
        float front = height * (1f - progress);
        float origin = Math.max(width * .08f, Math.min(width * .92f, originX));
        float span = Math.max(width * .18f, 24f * density);
        float amplitude = Math.min(14f * density, height * .10f) * tension;
        float direction = origin >= width * .5f ? 1f : -1f;
        float previousX = 0f;
        float previousY = bottomY(0f, front, origin, span, amplitude, direction);
        edge.moveTo(previousX, previousY);
        for (int i = 1; i <= SEGMENTS; i++) {
            float x = width * i / SEGMENTS;
            float y = bottomY(x, front, origin, span, amplitude, direction);
            if (i == SEGMENTS) edge.lineTo(x, y);
            else edge.quadTo(previousX, previousY, (previousX + x) * .5f, (previousY + y) * .5f);
            previousX = x;
            previousY = y;
        }
    }

    private static float bottomY(float x, float front, float origin, float span,
                                 float amplitude, float direction) {
        float u = (x - origin) / span;
        float bead = bell(1.4f * u * u);
        float wakeU = (u + direction * 1.24f) / 1.2f;
        float wake = bell(2f * wakeU * wakeU);
        return front + amplitude * (-bead + .23f * wake);
    }

    private void traceSide(float width, float height, boolean fromRight, float tension) {
        // Dragging uses the same elastic bead and wake as a dock tap. A small bulge
        // follows the finger's page direction, then settles without springing the UI.
        float front = width * (fromRight ? 1f - progress : progress);
        float amplitude = Math.min(13f * density, width * .12f) * tension;
        float direction = fromRight ? -1f : 1f;
        float previousY = 0f;
        float previousX = sideX(0f, height, front, amplitude, direction);
        edge.moveTo(previousX, previousY);
        for (int i = 1; i <= SEGMENTS; i++) {
            float y = height * i / SEGMENTS;
            float x = sideX(y, height, front, amplitude, direction);
            if (i == SEGMENTS) edge.lineTo(x, y);
            else edge.quadTo(previousX, previousY, (previousX + x) * .5f, (previousY + y) * .5f);
            previousX = x;
            previousY = y;
        }
    }

    private static float sideX(float y, float height, float front,
                               float amplitude, float direction) {
        float ratio = y / Math.max(1f, height);
        float beadU = (ratio - .63f) / .24f;
        float wakeU = (ratio - .27f) / .19f;
        return front + direction * amplitude * (bell(beadU * beadU) - .22f * bell(wakeU * wakeU));
    }

    private static float bell(float squaredDistance) {
        return (float) Math.exp(-squaredDistance);
    }

    void drawHighlight(Canvas canvas, float width, float height) {
        if (shape == 0 || isClosed() || isFullyOpen()) return;
        refreshGlint(width, height);
        float envelope = Math.min(1f, Math.min(progress * 6f, (1f - progress) * 6f));
        glow.setAlpha(Math.round(255f * envelope));
        rim.setAlpha(Math.round(255f * envelope));
        canvas.drawPath(edge, glow);
        canvas.drawPath(edge, rim);
    }

    private void refreshGlint(float width, float height) {
        int currentAccent = AppearanceTheme.accent(context);
        if (currentAccent == accent && glintWidth == width && glintHeight == height
                && glintShape == shape) return;
        accent = currentAccent;
        glintShape = shape;
        glintWidth = width;
        glintHeight = height;
        int pale = Color.rgb((Color.red(accent) + 255) / 2,
                (Color.green(accent) + 255) / 2, (Color.blue(accent) + 255) / 2);
        float dx = shape == PrimaryTabReveal.BOTTOM ? width : 0f;
        float dy = shape == PrimaryTabReveal.BOTTOM ? 0f : height;
        glow.setShader(new LinearGradient(0f, 0f, dx, dy,
                new int[]{accent & 0x00ffffff, withAlpha(accent, 22),
                        withAlpha(pale, 42), withAlpha(accent, 21), accent & 0x00ffffff},
                GLINT_STOPS, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0f, 0f, dx, dy,
                new int[]{accent & 0x00ffffff, withAlpha(accent, 40),
                        withAlpha(pale, 106), withAlpha(accent, 39), accent & 0x00ffffff},
                GLINT_STOPS, Shader.TileMode.CLAMP));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }
}
