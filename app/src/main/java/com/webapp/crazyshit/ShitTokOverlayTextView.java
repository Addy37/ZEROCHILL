package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;
import android.widget.TextView;

/** Keeps ShitTok copy readable without tinting the video behind it. */
final class ShitTokOverlayTextView extends TextView {
    ShitTokOverlayTextView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        setTextColor(Color.WHITE);
        setShadowLayer(1.5f * density, 0f, .5f * density, 0xD9000000);
    }

    @Override protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout != null && layout.getLineCount() > 0 && length() > 0) {
            TextPaint paint = getPaint();
            int color = paint.getColor();
            Paint.Style style = paint.getStyle();
            Paint.Join join = paint.getStrokeJoin();
            float stroke = paint.getStrokeWidth();
            int saved = canvas.save();
            try {
                canvas.translate(getCompoundPaddingLeft(), getBaseline() - layout.getLineBaseline(0));
                paint.clearShadowLayer();
                paint.setColor(0xE6000000);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(1.4f * getResources().getDisplayMetrics().density);
                layout.draw(canvas);
            } finally {
                canvas.restoreToCount(saved);
                paint.setColor(color);
                paint.setStyle(style);
                paint.setStrokeJoin(join);
                paint.setStrokeWidth(stroke);
                paint.setShadowLayer(getShadowRadius(), getShadowDx(), getShadowDy(), getShadowColor());
            }
        }
        super.onDraw(canvas);
    }
}
