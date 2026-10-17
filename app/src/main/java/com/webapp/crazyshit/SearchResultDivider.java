package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Transparent result rows share a pane; only a fine separator belongs to each row. */
final class SearchResultDivider extends Drawable {
    private final Paint paint = new Paint();
    private final float inset;
    SearchResultDivider(Context context) {
        paint.setColor(0x16FFFFFF);
        paint.setStrokeWidth(Math.max(1f, context.getResources().getDisplayMetrics().density * .5f));
        inset = BrowseUi.dp(context, 12);
    }
    @Override public void draw(Canvas canvas) {
        canvas.drawLine(getBounds().left + inset, getBounds().bottom - 1,
                getBounds().right - inset, getBounds().bottom - 1, paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
