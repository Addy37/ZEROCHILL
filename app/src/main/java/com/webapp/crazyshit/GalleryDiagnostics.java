package com.webapp.crazyshit;

import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;

/** Process-only bounded diagnostic events. Never stores titles, URLs, headers or exception messages. */
final class GalleryDiagnostics {
    private static final int LIMIT = 80;
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();
    private static final String SALT = UUID.randomUUID().toString();

    private GalleryDiagnostics() {}

    static void imageFailure(String stage, NativeContentItem item, String requested,
            String referer, Object model, GlideException error) {
        if (!ShitTokPoolDiagnostics.isEnabled()) return;
        String actualReferer = referer;
        if (model instanceof GlideUrl) {
            try { actualReferer = ((GlideUrl) model).getHeaders().get("Referer"); }
            catch (RuntimeException ignored) { }
        }
        int status = 0;
        String reason = "load_or_decode";
        if (error != null) {
            for (Throwable cause : error.getRootCauses()) {
                if (cause instanceof HttpException) {
                    status = ((HttpException) cause).getStatusCode();
                    reason = "http";
                    break;
                }
                if (cause instanceof java.net.SocketTimeoutException) reason = "timeout";
                else if (cause instanceof java.net.UnknownHostException) reason = "dns";
                else if (cause instanceof javax.net.ssl.SSLException) reason = "tls";
                else if (cause instanceof java.io.IOException && "load_or_decode".equals(reason))
                    reason = "io";
            }
        }
        record(stage, item, "request=" + hostLabel(requested) + " referer="
                + hostLabel(actualReferer) + " result=" + reason + " http=" + status);
    }

    static void missingImage(NativeContentItem item) {
        record("viewer_empty", item, "result=no_image_url");
    }

    static void resolveFailure(NativeContentItem item, boolean prefetch) {
        record("resolve_failed", item, "prefetch=" + prefetch);
    }

    static void navigation(NativeContentItem item, boolean creator, String destination) {
        record(creator ? "open_creator" : "open_item", item,
                "destination=" + hostLabel(destination));
    }

    static void imageReady(String stage, NativeContentItem item, String requested,
            int width, int height) {
        if (!ShitTokPoolDiagnostics.isEnabled() || item == null) return;
        // Keep successful recovery only for an item already seen in the failure ring.
        String key = token(item.url);
        synchronized (GalleryDiagnostics.class) {
            boolean seen = false;
            for (String line : EVENTS) {
                if (line.contains("item=" + key + " ") && !line.contains("result=ready")) {
                    seen = true;
                    break;
                }
            }
            if (!seen) return;
            record(stage, item, "request=" + hostLabel(requested) + " result=ready size="
                    + width + "x" + height);
        }
    }

    private static synchronized void record(String stage, NativeContentItem item, String detail) {
        if (!ShitTokPoolDiagnostics.isEnabled() || item == null) return;
        if (EVENTS.size() >= LIMIT) EVENTS.removeFirst();
        EVENTS.addLast(android.os.SystemClock.elapsedRealtime() + " " + stage
                + " item=" + token(item.url) + " media=" + hostLabel(item.url)
                + " artwork=" + hostLabel(item.imageUrl) + " creator=" + hostLabel(item.uploader)
                + " kind=" + (item.isImage() ? "image" : item.isVideo() ? "video" : "other")
                + " " + detail);
    }

    static synchronized String report() {
        return EVENTS.isEmpty() ? "No gallery failures or navigation events captured yet."
                : String.join("\n", EVENTS);
    }

    static synchronized void clearForTests() { EVENTS.clear(); }

    private static String token(String url) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((SALT + (url == null ? "" : url)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.lang.Long.toHexString(java.nio.ByteBuffer.wrap(hash).getLong());
        } catch (java.security.NoSuchAlgorithmException ignored) { return "unknown"; }
    }

    /** Fixed provider/host roles, not arbitrary host names or private URLs. */
    static String hostLabel(String value) {
        if (value == null || value.trim().isEmpty()) return "none";
        try {
            String host = URI.create(value).getHost();
            if (host == null) return "non_http";
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("img.cum.st")) return "onlyhaven_thumbnail";
            if (host.equals("media.cum.st")) return "onlyhaven_media";
            if (host.equals("cum.st") || host.endsWith(".cum.st")) return "onlyhaven";
            if (host.equals("balbums.com") || host.endsWith(".balbums.com")
                    || host.equals("balbum.com") || host.endsWith(".balbum.com")) return "balbums";
            if (host.equals("erome.com") || host.endsWith(".erome.com")) return "erome";
            if (host.equals("fapello.com") || host.endsWith(".fapello.com")) return "fapello";
            if (BunkrRepository.isBunkrUrl(value)) return "bunkr";
            if (OnlyHavenRepository.isOnlyHavenUrl(value)) return "onlyhaven";
            return "other";
        } catch (RuntimeException ignored) { return "invalid"; }
    }
}
