package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/** ImageView that renders a persisted normalized focus point and zoom for creator avatars. */
final class CreatorAvatarImageView extends ImageView {
    private boolean cropEnabled;
    private float focusX = CreatorAvatarOverrideStore.DEFAULT_FOCUS;
    private float focusY = CreatorAvatarOverrideStore.DEFAULT_FOCUS;
    private float zoom = CreatorAvatarOverrideStore.DEFAULT_ZOOM;

    CreatorAvatarImageView(Context context) {
        super(context);
        init();
    }

    CreatorAvatarImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setScaleType(ScaleType.CENTER_CROP);
    }

    void setAvatarCrop(float focusX, float focusY, float zoom) {
        cropEnabled = true;
        this.focusX = clamp(focusX, 0f, 1f);
        this.focusY = clamp(focusY, 0f, 1f);
        this.zoom = clamp(
                zoom,
                CreatorAvatarOverrideStore.DEFAULT_ZOOM,
                CreatorAvatarOverrideStore.MAX_ZOOM
        );
        setScaleType(ScaleType.MATRIX);
        applyCrop();
    }

    void clearAvatarCrop() {
        cropEnabled = false;
        focusX = CreatorAvatarOverrideStore.DEFAULT_FOCUS;
        focusY = CreatorAvatarOverrideStore.DEFAULT_FOCUS;
        zoom = CreatorAvatarOverrideStore.DEFAULT_ZOOM;
        setScaleType(ScaleType.CENTER_CROP);
    }

    void resetAvatarCrop() {
        setAvatarCrop(
                CreatorAvatarOverrideStore.DEFAULT_FOCUS,
                CreatorAvatarOverrideStore.DEFAULT_FOCUS,
                CreatorAvatarOverrideStore.DEFAULT_ZOOM
        );
    }

    void setAvatarZoom(float zoom) {
        setAvatarCrop(focusX, focusY, zoom);
    }

    void dragAvatarBy(float dx, float dy) {
        Drawable drawable = getDrawable();
        if (!cropEnabled || drawable == null || getWidth() <= 0 || getHeight() <= 0) return;
        float drawableWidth = Math.max(1f, drawable.getIntrinsicWidth());
        float drawableHeight = Math.max(1f, drawable.getIntrinsicHeight());
        float baseScale = Math.max(getWidth() / drawableWidth, getHeight() / drawableHeight);
        float scale = baseScale * zoom;
        focusX = clamp(focusX - dx / (drawableWidth * scale), 0f, 1f);
        focusY = clamp(focusY - dy / (drawableHeight * scale), 0f, 1f);
        applyCrop();
    }

    float avatarFocusX() {
        return focusX;
    }

    float avatarFocusY() {
        return focusY;
    }

    float avatarZoom() {
        return zoom;
    }

    /** Render the same square underneath the circular preview, with no extra crop. */
    Bitmap avatarBitmap(int side) {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() <= 0 || getHeight() <= 0) {
            throw new IllegalStateException("Wait for the image to load.");
        }
        applyCrop();
        Bitmap output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.BLACK);
        canvas.scale(side / (float) getWidth(), side / (float) getHeight());
        canvas.concat(getImageMatrix());
        android.graphics.Rect previous = new android.graphics.Rect(drawable.getBounds());
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        drawable.setBounds(previous);
        return output;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (cropEnabled) post(this::applyCrop);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (cropEnabled) post(this::applyCrop);
    }

    private void applyCrop() {
        if (!cropEnabled || getWidth() <= 0 || getHeight() <= 0) return;
        Drawable drawable = getDrawable();
        if (drawable == null) return;
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return;

        float baseScale = Math.max(
                getWidth() / (float) intrinsicWidth,
                getHeight() / (float) intrinsicHeight
        );
        float scale = baseScale * zoom;
        float scaledWidth = intrinsicWidth * scale;
        float scaledHeight = intrinsicHeight * scale;

        float translateX = getWidth() * 0.5f - focusX * scaledWidth;
        float translateY = getHeight() * 0.5f - focusY * scaledHeight;
        translateX = clamp(translateX, getWidth() - scaledWidth, 0f);
        translateY = clamp(translateY, getHeight() - scaledHeight, 0f);

        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(translateX, translateY);
        setImageMatrix(matrix);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
