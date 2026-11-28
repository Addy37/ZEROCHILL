package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.FutureTarget;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Explicitly requested, preview-sized artwork; no original media is retained. */
final class PreparedGalleryThumbnails {
    static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final int SIZE = 384;
    private static final Object LOCK = new Object();
    private static long usedBytes = -1;
    private static final java.util.Map<String, Long> COOLDOWNS = new java.util.HashMap<>();

    private PreparedGalleryThumbnails() {}

    private static File directory(Context context) {
        return new File(context.getApplicationContext().getNoBackupFilesDir(), "prepared_gallery_thumbnails");
    }

    private static File file(Context context, NativeContentItem item) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("v1\n" + item.kind + "\n" + item.url).getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : hash) name.append(String.format(java.util.Locale.ROOT, "%02x", b));
            return new File(directory(context), name + ".jpg");
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    static boolean eligible(NativeContentItem item) {
        return item != null && (item.isImage() || item.isVideo()) && !item.url.isEmpty();
    }

    static File cached(Context context, NativeContentItem item) {
        if (!eligible(item)) return null;
        File file = file(context, item);
        return file.isFile() && file.length() > 0 ? file : null;
    }

    static int count(Context context, List<NativeContentItem> items) {
        int count = 0;
        for (NativeContentItem item : items) if (eligible(item)) count++;
        return count;
    }

    static int ready(Context context, List<NativeContentItem> items) {
        int count = 0;
        for (NativeContentItem item : items) if (cached(context, item) != null) count++;
        return count;
    }

    /** Worker-thread only. Uses the same provider headers and bounded video extractor as the gallery. */
    static boolean prepare(Context context, NativeContentItem item) throws InterruptedException, CacheFull {
        if (!eligible(item)) return true;
        if (cached(context, item) != null) return true;
        Context app = context.getApplicationContext();
        String artwork = EromeRepository.gridArtwork(item);
        if (artwork.isEmpty()) artwork = item.imageUrl == null ? "" : item.imageUrl.trim();
        boolean distinctArtwork = !artwork.isEmpty() && !artwork.equals(item.url)
                && (!BunkrRepository.isBunkrUrl(item.url) || BunkrRepository.isUsableArtwork(artwork));
        if (distinctArtwork) {
            String host = android.net.Uri.parse(artwork).getHost();
            if (host == null) return false;
            // A provider's HTTP 429 pauses new background poster requests to that host.
            // Keep the existing shared Pawchive cadence intact.
            awaitHost(host);
            if (PawchiveRepository.isArtwork(artwork) || PawchiveRepository.isDeliveryFor(BunkrGalleryAdapter.imageReferer(item), artwork)) {
                try { PawchiveMediaRequests.awaitStart(); }
                catch (java.io.InterruptedIOException stopped) { throw new InterruptedException(); }
            }
            FutureTarget<Bitmap> target = Glide.with(app).asBitmap()
                    .load(BunkrGalleryAdapter.withHeaders(artwork, BunkrGalleryAdapter.imageReferer(item)))
                    .override(SIZE, SIZE).fitCenter().disallowHardwareConfig()
                    .diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true)
                    .priority(com.bumptech.glide.Priority.LOW).submit(SIZE, SIZE);
            try {
                Bitmap bitmap = target.get(35, TimeUnit.SECONDS);
                if (bitmap != null && BunkrMediaPreview.hasVisiblePixels(bitmap)) {
                    write(app, item, bitmap);
                    return true;
                }
            } catch (InterruptedException error) { throw error; }
            catch (CacheFull error) { throw error; }
            catch (Exception error) {
                if (isRateLimited(error)) {
                    synchronized (COOLDOWNS) {
                        if (COOLDOWNS.size() >= 128) COOLDOWNS.entrySet().removeIf(e -> e.getValue() <= android.os.SystemClock.elapsedRealtime());
                        COOLDOWNS.put(host, android.os.SystemClock.elapsedRealtime() + 30_000L);
                    }
                    return false; // Do not bypass a rate limit by switching to original media.
                }
            }
            finally { Glide.with(app).clear(target); }
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        // Original-only static pictures are not bulk downloaded. Video fallback uses range-based
        // frame extraction, not a full-file download. Unsupported sources remain explicitly missing.
        if (item.isVideo() && (PawchiveRepository.isNativeVideo(item)
                || BunkrRepository.isBunkrUrl(item.url))) {
            File preview = BunkrMediaPreview.cached(app, item);
            if (preview == null) {
                try { preview = BunkrMediaPreview.generateForIndex(app, item); }
                catch (Exception ignored) { }
            }
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (preview != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(preview.getAbsolutePath());
                if (bitmap != null) try {
                    if (BunkrMediaPreview.hasVisiblePixels(bitmap)) {
                        write(app, item, bitmap);
                        return true;
                    }
                } catch (CacheFull full) { throw full; }
                catch (IOException ignored) { }
                finally { bitmap.recycle(); }
            }
        }
        return false;
    }

    private static void awaitHost(String host) throws InterruptedException {
        while (true) {
            long wait;
            synchronized (COOLDOWNS) {
                wait = COOLDOWNS.getOrDefault(host, 0L) - android.os.SystemClock.elapsedRealtime();
            }
            if (wait <= 0) return;
            Thread.sleep(Math.min(wait, 500L));
        }
    }

    private static boolean isRateLimited(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof com.bumptech.glide.load.HttpException
                    && ((com.bumptech.glide.load.HttpException) cause).getStatusCode() == 429) return true;
            if (cause instanceof com.bumptech.glide.load.engine.GlideException)
                for (Throwable root : ((com.bumptech.glide.load.engine.GlideException) cause).getRootCauses())
                    if (root instanceof com.bumptech.glide.load.HttpException
                            && ((com.bumptech.glide.load.HttpException) root).getStatusCode() == 429) return true;
        }
        return false;
    }

    static final class CacheFull extends IOException {
        CacheFull() { super("Prepared thumbnail storage limit reached"); }
    }

    private static void write(Context context, NativeContentItem item, Bitmap bitmap) throws IOException {
        File destination = file(context, item);
        File dir = destination.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory())
            throw new IOException("Prepared cache unavailable");
        File temporary = File.createTempFile("prepare-", ".tmp", dir);
        try {
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream))
                    throw new IOException("Preview encode failed");
            }
            synchronized (LOCK) {
                if (destination.isFile() && destination.length() > 0) return;
                if (usedBytes < 0) {
                    usedBytes = 0;
                    File[] files = dir.listFiles((folder, name) -> name.endsWith(".jpg"));
                    if (files != null) for (File existing : files) {
                        usedBytes += existing.length();
                    }
                }
                if (usedBytes + temporary.length() > MAX_BYTES) throw new CacheFull();
                if (!temporary.renameTo(destination)) throw new IOException("Preview cache write failed");
                usedBytes += destination.length();
            }
        } finally { temporary.delete(); }
    }
}
