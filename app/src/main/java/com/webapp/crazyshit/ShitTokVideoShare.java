package com.webapp.crazyshit;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.content.FileProvider;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** On-demand progressive media export. Never substitutes a provider page for a video. */
@UnstableApi
final class ShitTokVideoShare {
    static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    static final long MAX_DIRECTORY_BYTES = 300L * 1024 * 1024;
    static final long RETENTION_MS = 48L * 60 * 60 * 1000;
    private static final long PREPARE_TIMEOUT_MS = 3L * 60 * 1000;
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        void onProgress(long bytes, long total);
        void onReady(Result result);
        void onError(String message);
    }

    static final class Result {
        final Uri uri;
        final String mime;
        final String title;
        final File file;

        Result(Uri uri, String mime, String title, File file) {
            this.uri = uri;
            this.mime = mime;
            this.title = title;
            this.file = file;
        }
    }

    static final class Job {
        private volatile boolean cancelled;
        private volatile Future<?> future;
        private volatile DataSource source;
        private volatile InputStream input;
        private final long started = SystemClock.elapsedRealtime();

        synchronized void cancel() {
            if (cancelled) return;
            cancelled = true;
            Future<?> current = future;
            if (current != null) current.cancel(true);
            // Closing on a worker keeps cancellation from blocking the UI on network I/O.
            DataSource data = source;
            InputStream stream = input;
            if (data != null || stream != null) {
                Thread closer = new Thread(() -> {
                    try { if (data != null) data.close(); } catch (Exception ignored) { }
                    try { if (stream != null) stream.close(); } catch (Exception ignored) { }
                }, "video-share-cancel");
                closer.setDaemon(true);
                closer.start();
            }
        }

        private void check() throws IOException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
            if (SystemClock.elapsedRealtime() - started > PREPARE_TIMEOUT_MS)
                throw new ShareUnavailable("Preparing this video timed out. Please try again.");
        }
    }

    private ShitTokVideoShare() { }

    static Job prepare(Activity activity, NativeContentItem item,
                       CrazyShitRepository.StreamInfo knownStream, Callback callback) {
        Job job = new Job();
        Context context = activity.getApplicationContext();
        job.future = WORK.submit(() -> {
            try {
                Result result = prepareBlocking(context, item, knownStream, job,
                        (bytes, total) -> MAIN.post(() -> {
                            if (!job.cancelled) callback.onProgress(bytes, total);
                        }));
                MAIN.post(() -> { if (!job.cancelled) callback.onReady(result); });
            } catch (Exception error) {
                String message = error instanceof ShareUnavailable ? error.getMessage()
                        : "Couldn't prepare this video. The source may be unavailable or restrict downloads. Try again later.";
                MAIN.post(() -> { if (!job.cancelled) callback.onError(message); });
            }
        });
        return job;
    }

    interface Progress { void update(long bytes, long total); }

    static Result prepareBlocking(Context context, NativeContentItem item,
            CrazyShitRepository.StreamInfo knownStream, Job job, Progress progress) throws Exception {
        if (item == null || item.url.isEmpty()) throw new ShareUnavailable("This video is unavailable.");
        File directory = new File(context.getCacheDir(), "shittok_share");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("No cache directory");
        cleanup(directory, System.currentTimeMillis());
        job.check();
        String key = cacheKey(item.url);
        for (String extension : new String[]{".mp4", ".webm"}) {
            File cached = new File(directory, key + extension);
            if (cached.isFile() && cached.length() > 0) {
                cached.setLastModified(System.currentTimeMillis());
                return result(context, cached, extension.equals(".mp4") ? "video/mp4" : "video/webm", item.title);
            }
        }
        File partial = new File(directory, key + ".part");
        try {
            job.check();
            Uri downloaded = completedDownload(context, item.url);
            boolean copiedDownload = false;
            if (downloaded != null) {
                try (InputStream input = context.getContentResolver().openInputStream(downloaded)) {
                    if (input != null) {
                        job.input = input;
                        copy(input::read, partial, C.LENGTH_UNSET, directory, job, progress);
                        copiedDownload = true;
                    }
                } catch (ShareUnavailable error) {
                    throw error;
                } catch (IOException error) {
                    job.check();
                    partial.delete();
                } finally { job.input = null; }
            }
            if (!copiedDownload) {
                CrazyShitRepository.StreamInfo stream = knownStream;
                if (stream == null || stream.mediaUrl == null || stream.mediaUrl.isEmpty())
                    stream = PlayableSourceRouter.resolve(context, item);
                job.check();
                if (stream == null || !supportsUrl(stream.mediaUrl))
                    throw new ShareUnavailable("This source doesn't provide a shareable video file. Live streams, protected media and segmented streams aren't supported.");
                DataSource source = ShitTokMediaSources.factory(context, stream, null).createDataSource();
                job.source = source;
                try {
                    long total = source.open(new DataSpec(Uri.parse(stream.mediaUrl)));
                    copy(source::read, partial, total, directory, job, progress);
                } finally {
                    try { source.close(); } finally { job.source = null; }
                }
            }
            job.check();
            String mime = validatedMime(partial);
            File file = new File(directory, key + (mime.equals("video/webm") ? ".webm" : ".mp4"));
            if (!partial.renameTo(file)) throw new IOException("Unable to retain video");
            file.setLastModified(System.currentTimeMillis());
            return result(context, file, mime, item.title);
        } finally {
            partial.delete();
        }
    }

    private static Result result(Context context, File file, String mime, String title) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".video-share", file);
        return new Result(uri, mime, title, file);
    }

    static Intent sendIntent(Result result) {
        Intent intent = new Intent(Intent.ACTION_SEND).setType(result.mime)
                .putExtra(Intent.EXTRA_STREAM, result.uri)
                .putExtra(Intent.EXTRA_SUBJECT, result.title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(new ClipData("ZEROCHILL video", new String[]{result.mime}, new ClipData.Item(result.uri)));
        return intent;
    }

    static boolean launch(Activity activity, Result result, String targetPackage) {
        Intent intent = sendIntent(result);
        if (targetPackage != null && !targetPackage.isEmpty()) intent.setPackage(targetPackage);
        try {
            activity.startActivity(targetPackage == null || targetPackage.isEmpty()
                    ? Intent.createChooser(intent, "Share video") : intent);
            // Keep the same file for repeat shares and give the receiving app a long read window.
            result.file.setLastModified(System.currentTimeMillis());
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            ZeroChillToast.makeText(activity,
                    "No compatible app is available. Try More Apps to share this video.", ZeroChillToast.LENGTH_LONG).show();
            return false;
        }
    }

    static boolean supportsUrl(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return false;
        String path = uri.getPath();
        String lower = path == null ? "" : path.toLowerCase(Locale.US);
        return !lower.contains(".m3u8") && !lower.contains(".mpd") && !lower.endsWith(".ism")
                && !lower.contains("/manifest") && !lower.endsWith(".m3u");
    }

    private static Uri completedDownload(Context context, String pageUrl) {
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        for (VideoDownloadStore.Entry entry : VideoDownloadStore.entries(context)) {
            if (!pageUrl.equals(entry.pageUrl) || entry.status != DownloadManager.STATUS_SUCCESSFUL) continue;
            if (entry.id >= 0 && manager != null) {
                Uri uri = manager.getUriForDownloadedFile(entry.id);
                if (uri != null) return uri;
            }
            if (!entry.localUri.isEmpty()) return Uri.parse(entry.localUri);
        }
        return null;
    }

    interface Reader { int read(byte[] buffer, int offset, int length) throws IOException; }

    static void copy(Reader reader, File output, long total, File directory,
                     Job job, Progress progress) throws IOException {
        long retained = directoryBytes(directory) - output.length();
        checkSize(total, retained);
        long bytes = 0;
        long lastProgress = 0;
        try (FileOutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while (true) {
                job.check();
                count = reader.read(buffer, 0, buffer.length);
                if (count == -1) break;
                if (count == 0) continue;
                bytes += count;
                checkSize(bytes, retained);
                out.write(buffer, 0, count);
                long now = SystemClock.elapsedRealtime();
                if (now - lastProgress >= 200) {
                    progress.update(bytes, total);
                    lastProgress = now;
                }
            }
            if (bytes == 0 || (total != C.LENGTH_UNSET && bytes != total))
                throw new ShareUnavailable("The video transfer was incomplete. Please try again.");
        }
        progress.update(bytes, total);
    }

    private static void checkSize(long bytes, long retained) throws ShareUnavailable {
        if (bytes > MAX_VIDEO_BYTES) throw new ShareUnavailable("This video is too large to share here. The limit is 100 MB.");
        if (bytes > 0 && bytes + retained > MAX_DIRECTORY_BYTES)
            throw new ShareUnavailable("Temporary sharing storage is full. Try again after older shared files expire.");
    }

    static String sniffMime(byte[] header) {
        if (header.length >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p')
            return "video/mp4";
        if (header.length >= 4 && (header[0] & 255) == 0x1a && (header[1] & 255) == 0x45
                && (header[2] & 255) == 0xdf && (header[3] & 255) == 0xa3
                && new String(header, StandardCharsets.ISO_8859_1).contains("webm")) return "video/webm";
        return "";
    }

    private static String validatedMime(File file) throws IOException {
        byte[] header = new byte[4096];
        try (FileInputStream in = new FileInputStream(file)) {
            if (in.read(header) < 12) throw new ShareUnavailable("This source didn't return a playable video file.");
        }
        String mime = sniffMime(header);
        if (mime.isEmpty()) throw new ShareUnavailable("This source's video format can't be shared. Only MP4 and WebM files are supported.");
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            if (extractor.getDrmInitData() != null || (extractor.getPsshInfo() != null && !extractor.getPsshInfo().isEmpty()))
                throw new ShareUnavailable("Protected videos can't be shared.");
            for (int index = 0; index < extractor.getTrackCount(); index++) {
                MediaFormat format = extractor.getTrackFormat(index);
                String trackMime = format.getString(MediaFormat.KEY_MIME);
                if (trackMime != null && trackMime.startsWith("video/")) return mime;
            }
            throw new ShareUnavailable("This source didn't return a playable video track.");
        } finally { extractor.release(); }
    }

    static String cacheKey(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder();
        for (byte part : digest) key.append(String.format(Locale.US, "%02x", part & 255));
        return key.toString();
    }

    static void cleanup(File directory, long now) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            // Never evict a recently shared file to make space: recipients may still be reading.
            if (file.isFile() && now - file.lastModified() > RETENTION_MS) file.delete();
        }
    }

    private static long directoryBytes(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) size += file.length();
        return size;
    }

    private static final class ShareUnavailable extends IOException {
        ShareUnavailable(String message) { super(message); }
    }
}
