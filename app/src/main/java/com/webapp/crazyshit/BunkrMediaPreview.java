package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Last-resort, visible-tile previews. Never creates a player or changes gallery media models. */
final class BunkrMediaPreview {
    private static final int SIZE = 384;
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;
    private static final long MAX_CACHE_BYTES = 32L * 1024 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(24));
    private static final LinkedHashMap<String, Long> FAILURES = new LinkedHashMap<>();
    private static final Pattern RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

    private BunkrMediaPreview() { }

    static Task request(Context context, NativeContentItem item, Consumer<File> callback) {
        Task task = new Task();
        Context app = context.getApplicationContext();
        try {
            task.future = IO.submit(() -> {
                task.deadline = SystemClock.elapsedRealtime() + BunkrRepository.requestTimeoutMs();
                File result = null;
                try {
                    task.check();
                    File cached = cacheFile(app, item);
                    if (cached.isFile() && cached.length() > 0) {
                        cached.setLastModified(System.currentTimeMillis());
                        result = cached;
                    } else if (!recentFailure(item.url)) {
                        result = generate(app, item, task);
                        if (result == null && !task.canceled) rememberFailure(item.url);
                    }
                } catch (Exception ignored) {
                    if (!task.canceled) rememberFailure(item.url);
                }
                File ready = result;
                MAIN.post(() -> { if (!task.canceled) callback.accept(ready); });
            });
        } catch (RejectedExecutionException ignored) {
            // A usable placeholder remains. A later attachment can try again.
            MAIN.post(() -> { if (!task.canceled) callback.accept(null); });
        }
        return task;
    }

    static File generate(Context context, NativeContentItem item, Task task) throws Exception {
        CrazyShitRepository.StreamInfo stream = new BunkrRepository().resolvePlayable(context, item.url);
        task.check();
        if (stream == null || stream.mediaUrl == null || stream.mediaUrl.isEmpty()) return null;
        Map<String, String> headers = headers(stream.mediaUrl, stream.requestReferer);
        Bitmap bitmap;
        if (item.isImage()) {
            bitmap = decodeImage(task.image(stream.mediaUrl, headers));
        } else if (item.isVideo()) {
            try (BunkrPreviewDataSource source = new BunkrPreviewDataSource(
                    (position, size) -> task.range(stream.mediaUrl, headers, position, size))) {
                // Give the native extractor a known length so it can seek to end-of-file metadata.
                if (source.readAt(0, new byte[1], 0, 1) < 0) return null;
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(source);
                    bitmap = frame(retriever, 1_000_000L);
                    if (bitmap == null) bitmap = frame(retriever, 0L);
                } finally { retriever.release(); }
            }
        } else return null;
        if (bitmap == null) return null;
        bitmap = scale(bitmap);
        try {
            task.check();
            File file = cacheFile(context, item);
            File dir = file.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Preview cache unavailable");
            File partial = new File(dir, file.getName() + ".tmp");
            try {
                try (FileOutputStream out = new FileOutputStream(partial)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out))
                        throw new IOException("Preview encoding failed");
                }
                task.check();
                if (!partial.renameTo(file)) throw new IOException("Preview cache write failed");
            } finally { partial.delete(); }
            trimCache(dir);
            return file;
        } finally { bitmap.recycle(); }
    }

    static Bitmap decodeImage(byte[] bytes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > SIZE * 2)
            options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (decoded == null) return null;
        decoded = scale(decoded);
        // BitmapFactory does not apply camera orientation when producing the cached JPEG.
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(bytes));
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
                default: return decoded;
            }
            Bitmap oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
            if (oriented != decoded) decoded.recycle();
            return oriented;
        } catch (IOException ignored) { return decoded; }
    }

    private static Bitmap frame(MediaMetadataRetriever retriever, long time) {
        if (android.os.Build.VERSION.SDK_INT >= 27)
            return retriever.getScaledFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, SIZE, SIZE);
        return retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
    }

    private static Bitmap scale(Bitmap decoded) {
        float scale = Math.min(1f, (float) SIZE / Math.max(decoded.getWidth(), decoded.getHeight()));
        if (scale == 1f) return decoded;
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, Math.max(1, Math.round(decoded.getWidth() * scale)),
                Math.max(1, Math.round(decoded.getHeight() * scale)), true);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }

    static File cacheFile(Context context, NativeContentItem item) throws Exception {
        // File-page identity remains stable when a resolved signed CDN URL expires.
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(("v1\n" + item.kind + "\n" + item.url).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder();
        for (byte value : hash) key.append(String.format(Locale.US, "%02x", value));
        return new File(new File(context.getCacheDir(), "bunkr_media_previews"), key + ".jpg");
    }

    private static void trimCache(File dir) {
        File[] files = dir.listFiles((folder, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long bytes = 0;
        for (File file : files) bytes += file.length();
        for (File file : files) {
            if (bytes <= MAX_CACHE_BYTES) break;
            long size = file.length();
            if (file.delete()) bytes -= size;
        }
    }

    private static synchronized boolean recentFailure(String page) {
        Long until = FAILURES.get(page);
        return until != null && until > SystemClock.elapsedRealtime();
    }
    private static synchronized void rememberFailure(String page) {
        FAILURES.put(page, SystemClock.elapsedRealtime() + 300_000L);
        while (FAILURES.size() > 256) FAILURES.remove(FAILURES.keySet().iterator().next());
    }

    static Map<String, String> headers(String url, String referer) {
        Map<String, String> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("User-Agent", BunkrRepository.userAgent());
        headers.put("Referer", BunkrRepository.referer(referer));
        SourceConfig current = RemoteSourceConfigManager.snapshotOrNull();
        if (current != null) headers.putAll(current.bunkr.requestHeaders);
        headers.put("Accept-Encoding", "identity");
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) headers.put("Cookie", cookies);
        } catch (Exception ignored) { }
        return headers;
    }

    static final class Task {
        volatile boolean canceled;
        private volatile HttpURLConnection connection;
        private Future<?> future;
        private long deadline = SystemClock.elapsedRealtime() + BunkrRepository.requestTimeoutMs();

        void cancel() {
            canceled = true;
            Future<?> old = future;
            if (old != null) { old.cancel(true); if (old instanceof Runnable) IO.remove((Runnable) old); }
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }
        void check() throws IOException {
            if (canceled || Thread.currentThread().isInterrupted()) throw new IOException("Preview canceled");
            if (SystemClock.elapsedRealtime() >= deadline) throw new IOException("Preview budget exhausted");
        }
        private HttpURLConnection open(String url, Map<String, String> headers) throws IOException {
            check();
            URL parsed = new URL(url);
            if (!"https".equals(parsed.getProtocol()) && !"http".equals(parsed.getProtocol()))
                throw new IOException("Invalid preview URL");
            HttpURLConnection conn = (HttpURLConnection) parsed.openConnection();
            connection = conn;
            int timeout = (int) Math.max(1, deadline - SystemClock.elapsedRealtime());
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setInstanceFollowRedirects(true);
            for (Map.Entry<String, String> header : headers.entrySet())
                conn.setRequestProperty(header.getKey(), header.getValue());
            check();
            return conn;
        }
        byte[] image(String url, Map<String, String> headers) throws IOException {
            HttpURLConnection conn = open(url, headers);
            try {
                String type = contentType(conn);
                if (conn.getResponseCode() != 200 || (!type.startsWith("image/")
                        && !type.startsWith("application/octet-stream")))
                    throw new IOException("No source image");
                if (conn.getContentLengthLong() > MAX_IMAGE_BYTES) throw new IOException("Image too large");
                try (InputStream in = conn.getInputStream()) { return read(in, MAX_IMAGE_BYTES, false); }
            } finally { conn.disconnect(); connection = null; }
        }
        BunkrPreviewDataSource.Block range(String url, Map<String, String> headers, long start, int size)
                throws IOException {
            HttpURLConnection conn = open(url, headers);
            try {
                conn.setRequestProperty("Range", "bytes=" + start + "-" + (start + size - 1));
                int status = conn.getResponseCode();
                if (contentType(conn).contains("html")) throw new IOException("No source video");
                long total;
                int expected;
                if (status == 206) {
                    Matcher range = RANGE.matcher(String.valueOf(conn.getHeaderField("Content-Range")));
                    if (!range.matches()) throw new IOException("Missing preview range");
                    try {
                        long from = Long.parseLong(range.group(1));
                        long to = Long.parseLong(range.group(2));
                        total = Long.parseLong(range.group(3));
                        if (from != start || to < from || to >= total || to - from >= size
                                || to != Math.min(total - 1, start + size - 1))
                            throw new IOException("Incorrect preview range");
                        expected = (int) (to - from + 1);
                    } catch (NumberFormatException error) { throw new IOException("Invalid preview range", error); }
                } else if (status == 200 && start == 0 && conn.getContentLengthLong() > 0
                        && conn.getContentLengthLong() <= size) {
                    total = conn.getContentLengthLong();
                    expected = (int) total;
                } else throw new IOException("Video does not support bounded preview reads");
                try (InputStream in = conn.getInputStream()) {
                    byte[] bytes = read(in, expected, true);
                    if (bytes.length != expected) throw new IOException("Incomplete preview range");
                    return new BunkrPreviewDataSource.Block(bytes, total);
                }
            } finally { conn.disconnect(); connection = null; }
        }
        private byte[] read(InputStream in, int limit, boolean exact) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            while (out.size() < limit) {
                check();
                int count = in.read(buffer, 0, Math.min(buffer.length, limit - out.size()));
                if (count < 0) return out.toByteArray();
                out.write(buffer, 0, count);
            }
            if (!exact && in.read() != -1) throw new IOException("Image too large");
            check();
            return out.toByteArray();
        }
        private String contentType(HttpURLConnection conn) {
            String type = conn.getContentType();
            return type == null ? "" : type.toLowerCase(Locale.US);
        }
    }
}
