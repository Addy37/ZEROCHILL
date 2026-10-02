package com.webapp.crazyshit;

import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;

/** Crossfade layers keep their own aspect ratio and share the viewport's center. */
final class GalleryFitCrossFade extends TransitionDrawable {
    private int viewportWidth;
    private int viewportHeight;

    GalleryFitCrossFade(Drawable preview, Drawable full, int width, int height) {
        super(new Drawable[]{preview, full});
        setViewport(width, height);
        setCrossFadeEnabled(true);
    }

    @Override public int getIntrinsicWidth() { return getDrawable(1).getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { return getDrawable(1).getIntrinsicHeight(); }

    void setViewport(int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        // Fit only while drawing; other targets may share Glide's resource.
    }

    Rect fittedLayerBounds(int layer) {
        Rect bounds = getBounds();
        Drawable full = getDrawable(1);
        Drawable child = getDrawable(layer);
        if (full.getIntrinsicWidth() <= 0 || full.getIntrinsicHeight() <= 0
                || child.getIntrinsicWidth() <= 0 || child.getIntrinsicHeight() <= 0) {
            return new Rect(bounds);
        }
        float fullScale = Math.min((float) viewportWidth / full.getIntrinsicWidth(),
                (float) viewportHeight / full.getIntrinsicHeight());
        float scale = Math.min((float) viewportWidth / child.getIntrinsicWidth(),
                (float) viewportHeight / child.getIntrinsicHeight()) / fullScale;
        int width = Math.round(child.getIntrinsicWidth() * scale);
        int height = Math.round(child.getIntrinsicHeight() * scale);
        int left = bounds.centerX() - width / 2;
        int top = bounds.centerY() - height / 2;
        return new Rect(left, top, left + width, top + height);
    }

    @Override public void draw(Canvas canvas) {
        Drawable preview = getDrawable(0), full = getDrawable(1);
        Rect previewBounds = new Rect(preview.getBounds()), fullBounds = new Rect(full.getBounds());
        Drawable.Callback previewCallback = preview.getCallback(), fullCallback = full.getCallback();
        // Temporary layer bounds must not enqueue a redraw or escape into another ImageView.
        preview.setCallback(null);
        full.setCallback(null);
        try {
            preview.setBounds(fittedLayerBounds(0));
            full.setBounds(fittedLayerBounds(1));
            super.draw(canvas);
        } finally {
            preview.setBounds(previewBounds);
            full.setBounds(fullBounds);
            preview.setCallback(previewCallback);
            full.setCallback(fullCallback);
        }
    }
}
