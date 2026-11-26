package com.webapp.crazyshit;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.security.MessageDigest;

/**
 * Drops the black pillarbox or letterbox around a Shows continue-watching frame
 * so a vertical video still fills the existing landscape card. Card size does
 * not change. A frame that is already full-bleed is left alone.
 */
final class ShowsContinueFrameCrop extends BitmapTransformation {
    private static final byte[] CACHE_KEY = "ShowsContinueFrameCrop.v1".getBytes(Key.CHARSET);
    private static final int SAMPLES = 24;
    private static final int COLOR_TOLERANCE = 12;
    /** 9:16 video inside a 16:9 player leaves about 34% black on each side. */
    private static final int MAX_BAR_NUMERATOR = 46;
    private static final int MAX_BAR_DENOMINATOR = 100;

    @Override
    protected Bitmap transform(
            @NonNull BitmapPool pool,
            @NonNull Bitmap bitmap,
            int outWidth,
            int outHeight
    ) {
        return crop(bitmap);
    }

    static Bitmap crop(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < 48 || bitmap.getHeight() < 48) return bitmap;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        try {
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        } catch (RuntimeException ignored) {
            return bitmap;
        }
        int[] region = region(pixels, width, height);
        if (region == null) return bitmap;
        return Bitmap.createBitmap(bitmap, region[0], region[1], region[2], region[3]);
    }

    /** @return left, top, width, height, or null when the frame should stay as-is. */
    static int[] region(int[] pixels, int width, int height) {
        if (pixels == null || width < 48 || height < 48 || pixels.length < width * height) return null;
        int[] sides = margins(pixels, width, height, true, 0, height);
        int[] ends = margins(pixels, width, height, false, sides[0], width - sides[1]);
        if ((sides[0] | sides[1] | ends[0] | ends[1]) == 0) return null;
        int cropWidth = width - sides[0] - sides[1];
        int cropHeight = height - ends[0] - ends[1];
        if (cropWidth < 24 || cropHeight < 24) return null;
        return new int[] { sides[0], ends[0], cropWidth, cropHeight };
    }

    private static int[] margins(
            int[] pixels,
            int width,
            int height,
            boolean columns,
            int start,
            int end
    ) {
        int span = columns ? width : height;
        if (end - start < 12) return new int[2];
        int midpoint = start + (end - start) / 2;
        int color = pixel(pixels, width, columns, 0, midpoint);
        if ((!transparent(color) && !darkNeutral(color))
                || !matches(pixel(pixels, width, columns, span - 1, midpoint), color)) {
            return new int[2];
        }
        int limit = span * MAX_BAR_NUMERATOR / MAX_BAR_DENOMINATOR;
        int first = stripWidth(pixels, width, columns, 0, 1, start, end, color, limit);
        int last = stripWidth(pixels, width, columns, span - 1, -1, start, end, color, limit);
        int minimum = Math.max(4, span / 24);
        boolean symmetric = Math.abs(first - last) <= Math.max(2, Math.min(first, last) / 6);
        return first >= minimum && last >= minimum && first < limit && last < limit && symmetric
                ? new int[] { first, last } : new int[2];
    }

    private static int stripWidth(
            int[] pixels,
            int width,
            boolean columns,
            int edge,
            int step,
            int start,
            int end,
            int color,
            int limit
    ) {
        int inset = (end - start) / 12;
        int first = start + inset;
        int sampleSpan = Math.max(0, end - 1 - inset - first);
        for (int offset = 0; offset < limit; offset++) {
            int line = edge + offset * step;
            for (int i = 0; i < SAMPLES; i++) {
                int position = first + (SAMPLES == 1 ? 0 : i * sampleSpan / (SAMPLES - 1));
                if (!matches(pixel(pixels, width, columns, line, position), color)) return offset;
            }
        }
        return limit;
    }

    private static int pixel(int[] pixels, int width, boolean columns, int line, int position) {
        return columns ? pixels[position * width + line] : pixels[line * width + position];
    }

    private static boolean darkNeutral(int color) {
        int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return (color >>> 24) >= 240 && max <= 48 && max - min <= COLOR_TOLERANCE;
    }

    private static boolean transparent(int color) {
        return (color >>> 24) <= 12;
    }

    private static boolean matches(int pixel, int color) {
        if (transparent(color)) return transparent(pixel);
        return darkNeutral(pixel)
                && Math.abs(((pixel >>> 16) & 255) - ((color >>> 16) & 255)) <= COLOR_TOLERANCE
                && Math.abs(((pixel >>> 8) & 255) - ((color >>> 8) & 255)) <= COLOR_TOLERANCE
                && Math.abs((pixel & 255) - (color & 255)) <= COLOR_TOLERANCE;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest digest) {
        digest.update(CACHE_KEY);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShowsContinueFrameCrop;
    }

    @Override
    public int hashCode() {
        return ShowsContinueFrameCrop.class.getName().hashCode();
    }
}
