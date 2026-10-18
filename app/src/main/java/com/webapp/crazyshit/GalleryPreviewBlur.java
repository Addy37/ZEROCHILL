package com.webapp.crazyshit;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;

/** Bounded, independently owned preview pixels. Never modifies a Glide bitmap. */
final class GalleryPreviewBlur {
    private static final int MAX_EDGE = 128;

    interface Drawer { void draw(Canvas canvas); }

    private GalleryPreviewBlur() { }

    static void applyHardware(View view, boolean enabled) {
        if (Build.VERSION.SDK_INT >= 31) Api31.apply(view, enabled);
    }

    static Bitmap snapshot(View view, Drawer drawer) {
        return snapshot(view, drawer, true);
    }

    static Bitmap snapshot(View view, Drawer drawer, boolean soften) {
        int width = view.getWidth(), height = view.getHeight();
        if (width <= 0 || height <= 0) return null;
        float scale = Math.min(1f, (float) MAX_EDGE / Math.max(width, height));
        int smallWidth = Math.max(1, Math.round(width * scale));
        int smallHeight = Math.max(1, Math.round(height * scale));
        Bitmap bitmap = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale((float) smallWidth / width, (float) smallHeight / height);
        drawer.draw(canvas);
        if (!soften) return bitmap;
        soften(bitmap, 2);
        return bitmap;
    }

    static void soften(Bitmap bitmap, int radius) {
        int smallWidth = bitmap.getWidth(), smallHeight = bitmap.getHeight();
        int[] pixels = new int[smallWidth * smallHeight];
        int[] scratch = new int[pixels.length];
        bitmap.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight);
        for (int pass = 0; pass < 2; pass++) {
            blur(pixels, scratch, smallWidth, smallHeight, true, radius);
            blur(scratch, pixels, smallWidth, smallHeight, false, radius);
        }
        bitmap.setPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight);
    }

    private static void blur(int[] source, int[] output, int width, int height, boolean horizontal, int radius) {
        int lines = horizontal ? height : width;
        int length = horizontal ? width : height;
        int step = horizontal ? 1 : width;
        for (int line = 0; line < lines; line++) {
            int base = horizontal ? line * width : line;
            for (int position = 0; position < length; position++) {
                int a = 0, r = 0, g = 0, b = 0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int pixel = source[base + Math.max(0, Math.min(length - 1, position + offset)) * step];
                    a += pixel >>> 24;
                    r += (pixel >>> 16) & 255;
                    g += (pixel >>> 8) & 255;
                    b += pixel & 255;
                }
                int count = radius * 2 + 1;
                output[base + position * step] = ((a / count) << 24) | ((r / count) << 16)
                        | ((g / count) << 8) | (b / count);
            }
        }
    }

    @RequiresApi(31)
    private static final class Api31 {
        static void apply(View view, boolean enabled) {
            float radius = 12f * view.getResources().getDisplayMetrics().density;
            view.setRenderEffect(enabled
                    ? RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL) : null);
        }
    }
}
