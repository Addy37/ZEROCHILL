package com.webapp.crazyshit;

import java.util.Locale;

/** Bunkr-only fallback routing. Ordinary MP4/HLS/DASH stays on Media3. */
final class LegacyVideoFormatSupport {
    private static final int UNSUPPORTED_CONTAINER = 3003;

    private LegacyVideoFormatSupport() { }

    static boolean requiresVlc(String mediaUrl) {
        String path = path(mediaUrl);
        return path.endsWith(".wmv") || path.endsWith(".asf");
    }

    /** Media3 already handles common formats; try VLC only after a container parse failure. */
    static boolean retryWithVlc(String mediaUrl, int errorCode) {
        if (errorCode != UNSUPPORTED_CONTAINER) return false;
        String path = path(mediaUrl);
        if (path.isEmpty()) return false;
        return !path.endsWith(".mp4") && !path.endsWith(".m4v")
                && !path.endsWith(".m3u8") && !path.endsWith(".mpd");
    }

    static boolean validHttpsMedia(String mediaUrl) {
        if (mediaUrl == null) return false;
        try {
            java.net.URI uri = new java.net.URI(mediaUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isEmpty()
                    && uri.getUserInfo() == null;
        } catch (java.net.URISyntaxException error) {
            return false;
        }
    }

    private static String path(String mediaUrl) {
        if (mediaUrl == null) return "";
        int cut = mediaUrl.length();
        int query = mediaUrl.indexOf('?');
        int fragment = mediaUrl.indexOf('#');
        if (query >= 0) cut = Math.min(cut, query);
        if (fragment >= 0) cut = Math.min(cut, fragment);
        return mediaUrl.substring(0, cut).toLowerCase(Locale.US);
    }
}
