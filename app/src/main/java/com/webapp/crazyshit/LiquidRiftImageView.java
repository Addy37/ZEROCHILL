package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.widget.ImageView;

/** Hero-local, finite glass wave. Reuses the Glide drawable and never snapshots a bitmap. */
final class LiquidRiftImageView extends ImageView {
    private final Path wave = new Path();
    private final Path region = new Path();
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float reveal = 1f;
    private int blurLevel;
    private RenderEffect[] blurs;

    LiquidRiftImageView(Context context) {
        super(context);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
    }

    void rift(float reveal, float focus) {
        this.reveal = reveal;
        int level = Math.round(Math.max(0f, Math.min(1f, focus)) * 8f);
        if (Build.VERSION.SDK_INT >= 31 && level != blurLevel) {
            if (level > 0 && blurs == null) {
                blurs = new RenderEffect[9];
                for (int i = 1; i <= 8; i++) {
                    float radius = i * getResources().getDisplayMetrics().density;
                    blurs[i] = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);
                }
            }
            setRenderEffect(level == 0 ? null : blurs[level]);
        }
        blurLevel = level;
        invalidate();
    }

    void resetRift() {
        rift(1f, 0f);
        setScaleX(1f); setScaleY(1f); setTranslationX(0f);
        blurs = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (reveal >= 1f) { super.onDraw(canvas); return; }
        if (reveal <= 0f) return;
        float w = getWidth(), h = getHeight();
        float bend = w * .075f * (float) Math.sin(Math.PI * reveal);
        float x = -w * .12f + w * 1.24f * reveal;
        wave.rewind();
        wave.moveTo(x, 0);
        wave.cubicTo(x + bend * 1.8f, h * .22f, x - bend * 1.3f, h * .37f, x, h * .53f);
        wave.cubicTo(x + bend, h * .70f, x - bend, h * .86f, x, h);
        region.set(wave); region.lineTo(0, h); region.lineTo(0, 0); region.close();
        int save = canvas.save();
        canvas.clipPath(region);
        super.onDraw(canvas);
        // A narrow displaced copy of the same artwork refracts only the traveling boundary.
        canvas.clipRect(x - w * .025f, 0, x + bend * 2f, h);
        canvas.translate(bend * .09f, 0);
        super.onDraw(canvas);
        canvas.restoreToCount(save);
        rim.setColor(0xFF0892D0);
        rim.setAlpha(Math.round(105f * (float) Math.sin(Math.PI * reveal)));
        canvas.drawPath(wave, rim);
    }
}
