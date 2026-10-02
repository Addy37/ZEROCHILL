package com.webapp.crazyshit;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;

/** Crossfade layers keep their own aspect ratio and share the viewport's center. */
final class GalleryFitCrossFade extends TransitionDrawable {
    private int viewportWidth;
    private int viewportHeight;

    GalleryFitCrossFade(Drawable preview, Drawable full, int width, int height) {
        super(new Drawable[]{new FittedLayer(preview), new FittedLayer(full)});
        ((FittedLayer) super.getDrawable(0)).owner = this;
        ((FittedLayer) super.getDrawable(1)).owner = this;
        setViewport(width, height);
        setCrossFadeEnabled(true);
    }

    @Override public Drawable getDrawable(int index) {
        return ((FittedLayer) super.getDrawable(index)).resource;
    }

    @Override public int getIntrinsicWidth() { return getDrawable(1).getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { return getDrawable(1).getIntrinsicHeight(); }

    void setViewport(int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
    }

    RectF fittedLayerBounds(int layer) {
        RectF fitted = new RectF();
        fitLayerBounds(getDrawable(layer), fitted);
        return fitted;
    }

    private void fitLayerBounds(Drawable child, RectF fitted) {
        Rect bounds = getBounds();
        Drawable full = getDrawable(1);
        if (full.getIntrinsicWidth() <= 0 || full.getIntrinsicHeight() <= 0
                || child.getIntrinsicWidth() <= 0 || child.getIntrinsicHeight() <= 0) {
            fitted.set(bounds);
            return;
        }
        float fullScale = Math.min((float) viewportWidth / full.getIntrinsicWidth(),
                (float) viewportHeight / full.getIntrinsicHeight());
        float scale = Math.min((float) viewportWidth / child.getIntrinsicWidth(),
                (float) viewportHeight / child.getIntrinsicHeight()) / fullScale;
        float width = child.getIntrinsicWidth() * scale;
        float height = child.getIntrinsicHeight() * scale;
        float left = bounds.exactCenterX() - width / 2f;
        float top = bounds.exactCenterY() - height / 2f;
        fitted.set(left, top, left + width, top + height);
    }

    /** Wrapper alpha/bounds are independent of Glide's drawable and use subpixel fitting. */
    private static final class FittedLayer extends Drawable {
        private final Drawable resource;
        private GalleryFitCrossFade owner;
        private final Rect originalBounds = new Rect();
        private final RectF fittedBounds = new RectF();
        private int opacity = 255;

        FittedLayer(Drawable resource) { this.resource = resource; }

        @Override public int getIntrinsicWidth() { return resource.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return resource.getIntrinsicHeight(); }
        @Override public void setAlpha(int alpha) { opacity = alpha; }
        @Override public int getAlpha() { return opacity; }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void setColorFilter(ColorFilter filter) { }

        @Override public void draw(Canvas canvas) {
            if (owner == null || opacity == 0) return;
            owner.fitLayerBounds(resource, fittedBounds);
            int width = resource.getIntrinsicWidth(), height = resource.getIntrinsicHeight();
            if (width <= 0 || height <= 0) return;
            originalBounds.set(resource.getBounds());
            int originalAlpha = resource.getAlpha();
            Drawable.Callback originalCallback = resource.getCallback();
            int save = canvas.save();
            resource.setCallback(null);
            try {
                canvas.translate(fittedBounds.left, fittedBounds.top);
                canvas.scale(fittedBounds.width() / width, fittedBounds.height() / height);
                resource.setBounds(0, 0, width, height);
                resource.setAlpha(originalAlpha * opacity / 255);
                resource.draw(canvas);
            } finally {
                resource.setBounds(originalBounds);
                resource.setAlpha(originalAlpha);
                resource.setCallback(originalCallback);
                canvas.restoreToCount(save);
            }
        }
    }
}
