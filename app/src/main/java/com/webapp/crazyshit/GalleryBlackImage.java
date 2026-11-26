package com.webapp.crazyshit;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Conservative check of a bounded, decoded static image. No cached or persisted blacklist. */
final class GalleryBlackImage {
    private static final int MAX_PIXELS = 512 * 512;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private GalleryBlackImage() { }

    static boolean eligible(NativeContentItem item) {
        if (item == null || !item.isImage() || !OnlyHavenRepository.isOnlyHavenUrl(item.uploader)) return false;
        String path;
        try { path = java.net.URI.create(item.url).getPath(); }
        catch (RuntimeException invalid) { return false; }
        if (path == null) return false;
        path = path.toLowerCase(java.util.Locale.ROOT);
        // Only static image originals, never videos, animated GIFs or ambiguous post pages.
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png")
                || path.endsWith(".webp") || path.endsWith(".avif");
    }

    enum FailureAction { KEEP, RETRY, OMIT }

    /** Never omit on a preview failure alone, nor on any video/GIF failure. */
    static FailureAction originalFailureAction(NativeContentItem item, String requested,
            boolean finalAttempt) {
        if (!eligible(item) || !item.url.equals(requested)) return FailureAction.KEEP;
        return finalAttempt ? FailureAction.OMIT : FailureAction.RETRY;
    }

    /** Snapshot pixels while Glide owns the resource. Scan the owned array off the UI thread. */
    static void check(Drawable drawable, java.util.function.Consumer<Boolean> result) {
        if (!(drawable instanceof BitmapDrawable)) return;
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w <= 0 || h <= 0 || (long) w * h > MAX_PIXELS) return;
        try {
            // Fast rejection for normal images; avoid copying them or scheduling work.
            if (bitmap.getPixel(0, 0) != Color.BLACK
                    || bitmap.getPixel(w / 2, h / 2) != Color.BLACK
                    || bitmap.getPixel(w - 1, h - 1) != Color.BLACK
                    || bitmap.getPixel(w - 1, 0) != Color.BLACK
                    || bitmap.getPixel(0, h - 1) != Color.BLACK) return;
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            WORKER.execute(() -> {
                if (allOpaqueBlack(pixels)) result.accept(true);
            });
        } catch (RuntimeException unavailable) {
            // Hardware, recycled or unreadable bitmaps are never suppressed.
        }
    }

    static boolean allOpaqueBlack(int[] pixels) {
        if (pixels == null || pixels.length == 0) return false;
        for (int pixel : pixels) if (pixel != Color.BLACK) return false;
        return true;
    }
}
