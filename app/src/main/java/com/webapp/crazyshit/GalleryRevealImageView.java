package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.widget.ImageView;

/** A soft descending matte over an already-decoded thumbnail. No layout or bitmap allocations per frame. */
final class GalleryRevealImageView extends ImageView {
    static final int MATTE = Color.rgb(30, 34, 40);
    static final long DURATION_MS = 350L;
    static final long STAGGER_MS = 65L;
    static final long SETTLE_MS = 110L;
    private final Paint paint = new Paint();
    private final Matrix matrix = new Matrix();
    private final LinearGradient gradient = new LinearGradient(0f, 0f, 0f, 1f,
            new int[]{MATTE & 0x00ffffff, MATTE}, null, Shader.TileMode.CLAMP);
    private float progress = 1f;

    GalleryRevealImageView(Context context) { super(context); }

    void setRevealProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    static float front(float progress, float height, float band) {
        return progress * (height + band) - band;
    }

    static boolean canStart(int scrollState, long sinceFastScroll) {
        return scrollState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_SETTLING
                && sinceFastScroll >= SETTLE_MS;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (progress >= 1f) { super.onDraw(canvas); return; }
        paint.setShader(null);
        paint.setColor(MATTE);
        if (progress <= 0f) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            return;
        }
        super.onDraw(canvas);
        float height = getHeight();
        float band = Math.max(1f, height * .36f);
        float edge = front(progress, height, band);
        matrix.setScale(1f, band);
        matrix.postTranslate(0f, edge);
        gradient.setLocalMatrix(matrix);
        paint.setShader(gradient);
        canvas.drawRect(0f, 0f, getWidth(), height, paint);
        paint.setShader(null);
    }
}
