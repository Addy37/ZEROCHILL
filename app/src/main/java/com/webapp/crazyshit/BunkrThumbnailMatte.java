package com.webapp.crazyshit;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.security.MessageDigest;

/** Removes matching dark or transparent padding embedded in a still Bunkr thumbnail. */
final class BunkrThumbnailMatte extends BitmapTransformation {
    private static final byte[] CACHE_KEY = "BunkrThumbnailMatte.v1".getBytes(Key.CHARSET);
    private static final int SAMPLES = 24;
    private static final int COLOR_TOLERANCE = 12;

    @Override protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap bitmap,
            int outWidth, int outHeight) {
        if (bitmap.getWidth() < 48 || bitmap.getHeight() < 48) return bitmap;
        int[] sides = margins(bitmap, true, 0, bitmap.getHeight());
        int[] ends = margins(bitmap, false, sides[0], bitmap.getWidth() - sides[1]);
        if ((sides[0] | sides[1] | ends[0] | ends[1]) == 0) return bitmap;
        return Bitmap.createBitmap(bitmap, sides[0], ends[0],
                bitmap.getWidth() - sides[0] - sides[1],
                bitmap.getHeight() - ends[0] - ends[1]);
    }

    private static int[] margins(Bitmap bitmap, boolean columns, int start, int end) {
        int span = columns ? bitmap.getWidth() : bitmap.getHeight();
        int midpoint = start + (end - start) / 2;
        int color = pixel(bitmap, columns, 0, midpoint);
        if ((!transparent(color) && !darkNeutral(color)) || !matches(pixel(bitmap, columns, span - 1, midpoint), color)) {
            return new int[2];
        }
        int limit = span / 3;
        int first = stripWidth(bitmap, columns, 0, 1, start, end, color, limit);
        int last = stripWidth(bitmap, columns, span - 1, -1, start, end, color, limit);
        int minimum = Math.max(4, span / 24);
        boolean symmetric = Math.abs(first - last) <= Math.max(2, Math.min(first, last) / 6);
        return first >= minimum && last >= minimum && first < limit && last < limit && symmetric
                ? new int[]{first, last} : new int[2];
    }

    private static int stripWidth(Bitmap bitmap, boolean columns, int edge, int step, int start,
            int end, int color, int limit) {
        int inset = (end - start) / 12;
        int first = start + inset;
        int sampleSpan = end - 1 - inset - first;
        for (int offset = 0; offset < limit; offset++) {
            int line = edge + offset * step;
            for (int i = 0; i < SAMPLES; i++) {
                int position = first + i * sampleSpan / (SAMPLES - 1);
                if (!matches(pixel(bitmap, columns, line, position), color)) return offset;
            }
        }
        return limit;
    }

    private static int pixel(Bitmap bitmap, boolean columns, int line, int position) {
        return columns ? bitmap.getPixel(line, position) : bitmap.getPixel(position, line);
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

    @Override public void updateDiskCacheKey(@NonNull MessageDigest digest) {
        digest.update(CACHE_KEY);
    }

    @Override public boolean equals(Object other) {
        return other instanceof BunkrThumbnailMatte;
    }

    @Override public int hashCode() {
        return BunkrThumbnailMatte.class.getName().hashCode();
    }
}
