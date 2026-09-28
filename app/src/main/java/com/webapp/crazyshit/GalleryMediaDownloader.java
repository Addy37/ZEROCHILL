package com.webapp.crazyshit;

import android.app.Activity;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;

/** Downloads resolved gallery photos directly and reuses the existing video download stack. */
final class GalleryMediaDownloader {
    interface ResolutionListener {
        void onResolved(String mediaUrl, String requestReferer);
    }

    private static final String FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private GalleryMediaDownloader() {
    }

    static void download(
            Activity activity,
            ExecutorService io,
            NativeContentItem item,
            String resolvedUrl,
            String requestReferer,
            ResolutionListener listener
    ) {
        if (activity == null || item == null) return;
        if (item.isVideo()) {
            downloadVideo(activity, item, resolvedUrl, requestReferer);
            return;
        }
        if (!item.isImage()) return;

        String mediaUrl = value(resolvedUrl);
        if (mediaUrl.isEmpty() &&
                (isOnlyHavenDirectImage(item) || isCoomerFansDirectImage(item))) {
            mediaUrl = value(item.url);
        }
        String referer = value(requestReferer);
        if (referer.isEmpty()) referer = imageReferer(item);

        if (!mediaUrl.isEmpty()) {
            enqueueImage(activity, item, mediaUrl, referer);
            return;
        }
        if (io == null) {
            toast(activity, "Couldn't prepare this image for download.");
            return;
        }

        toast(activity, "Preparing image download…");
        String fallbackReferer = referer;
        io.execute(() -> {
            try {
                CrazyShitRepository.StreamInfo resolved =
                        PlayableSourceRouter.resolve(activity, item.url);
                if (resolved == null || value(resolved.mediaUrl).isEmpty()) {
                    throw new IllegalStateException("No resolved image URL");
                }
                String resolvedReferer = value(resolved.requestReferer);
                if (resolvedReferer.isEmpty()) resolvedReferer = fallbackReferer;
                String finalReferer = resolvedReferer;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (listener != null) {
                        listener.onResolved(resolved.mediaUrl, finalReferer);
                    }
                    enqueueImage(activity, item, resolved.mediaUrl, finalReferer);
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        toast(activity, "Couldn't prepare this image for download.");
                    }
                });
            }
        });
    }

    private static void downloadVideo(
            Activity activity,
            NativeContentItem item,
            String resolvedUrl,
            String requestReferer
    ) {
        String mediaUrl = value(resolvedUrl);
        if (mediaUrl.isEmpty() && isOnlyHavenDirectVideo(item)) {
            mediaUrl = value(item.url);
        }
        if (mediaUrl.isEmpty()) {
            VideoDownloadStore.downloadPage(activity, item);
            return;
        }

        String referer = value(requestReferer);
        if (referer.isEmpty()) referer = value(item.url);
        VideoDownloadStore.downloadKnown(
                activity,
                item.title,
                item.url,
                item.imageUrl,
                mediaUrl,
                userAgent(activity),
                cookies(mediaUrl, referer),
                referer
        );
    }

    private static void enqueueImage(
            Activity activity,
            NativeContentItem item,
            String mediaUrl,
            String requestReferer
    ) {
        String lower = value(mediaUrl).toLowerCase(Locale.US);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            toast(activity, "This image can't be downloaded.");
            return;
        }

        DownloadManager manager =
                (DownloadManager) activity.getSystemService(Activity.DOWNLOAD_SERVICE);
        if (manager == null) {
            toast(activity, "Android's download service isn't available.");
            return;
        }

        String referer = value(requestReferer);
        String mime = imageMimeType(mediaUrl);
        String fileName = imageFileName(item, mediaUrl, mime);
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mediaUrl));
            request.setTitle(value(item.title).isEmpty() ? "ZeroChill image" : item.title);
            request.setDescription("ZeroChill image");
            request.setMimeType(mime);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
            );
            request.addRequestHeader("User-Agent", userAgent(activity));
            if (!referer.isEmpty()) request.addRequestHeader("Referer", referer);
            String cookies = cookies(mediaUrl, referer);
            if (!cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            manager.enqueue(request);
            toast(activity, "Download started in Downloads.");
        } catch (Exception error) {
            toast(activity, "Couldn't start the image download.");
        }
    }

    private static String imageReferer(NativeContentItem item) {
        if (item == null) return "";
        if (WikiFeetRepository.isWikiFeetUrl(item.url)
                && WikiFeetRepository.isWikiFeetUrl(item.uploader)) {
            return value(item.uploader);
        }
        if (!FapelloRepository.isPostUrl(item.url)
                && FapelloRepository.isModelUrl(item.uploader)) {
            return value(item.uploader);
        }
        if (isOnlyHavenDirectImage(item) && !value(item.uploader).isEmpty()) {
            return value(item.uploader);
        }
        if (isCoomerFansDirectImage(item) && !value(item.uploader).isEmpty()) {
            return value(item.uploader);
        }
        return value(item.url);
    }

    private static boolean isOnlyHavenDirectImage(NativeContentItem item) {
        return item != null
                && item.isImage()
                && OnlyHavenRepository.isOnlyHavenUrl(item.url)
                && OnlyHavenRepository.isDirectImageUrl(item.url);
    }

    private static boolean isCoomerFansDirectImage(NativeContentItem item) {
        return item != null
                && item.isImage()
                && CoomerFansRepository.isDirectImageUrl(item.url);
    }

    private static boolean isOnlyHavenDirectVideo(NativeContentItem item) {
        if (item == null || !item.isVideo()) return false;
        boolean onlyHaven = OnlyHavenRepository.isOnlyHavenUrl(item.uploader)
                || value(item.description).toLowerCase(Locale.US).contains("onlyhaven");
        if (!onlyHaven) return false;
        String lower = value(item.url).toLowerCase(Locale.US);
        return lower.matches(".*\\.(?:mp4|m3u8|mpd|webm|m4v)(?:\\?.*)?$");
    }

    private static String imageMimeType(String url) {
        String lower = value(url).toLowerCase(Locale.US);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".avif")) return "image/avif";
        return "image/jpeg";
    }

    private static String imageFileName(
            NativeContentItem item,
            String mediaUrl,
            String mime
    ) {
        String title = value(item == null ? "" : item.title)
                .replaceAll("[^A-Za-z0-9._ -]+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (title.isEmpty()) title = "ZeroChill image";
        if (title.length() > 70) title = title.substring(0, 70).trim();
        String extension = "image/png".equals(mime) ? ".png"
                : "image/webp".equals(mime) ? ".webp"
                : "image/gif".equals(mime) ? ".gif"
                : "image/avif".equals(mime) ? ".avif"
                : ".jpg";
        return title + "-" + Integer.toHexString(value(mediaUrl).hashCode()) + extension;
    }

    private static String cookies(String mediaUrl, String referer) {
        try {
            String cookies = CookieManager.getInstance().getCookie(mediaUrl);
            if ((cookies == null || cookies.isEmpty()) && !referer.isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(referer);
            }
            return value(cookies);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String userAgent(Activity activity) {
        try {
            return WebSettings.getDefaultUserAgent(activity);
        } catch (Exception ignored) {
            return FALLBACK_USER_AGENT;
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static void toast(Activity activity, String message) {
        activity.runOnUiThread(() ->
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        );
    }
}
