package com.webapp.crazyshit;

import android.os.SystemClock;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Short-lived, in-memory evidence of maintenance for individual Bunkr files. */
final class BunkrMaintenance {
    private static final long TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_ENTRIES = 256;
    private static final Pattern FILE_PATH = Pattern.compile("(?i)^/([fvid])/([^/]+)/?$");
    private static final String FILE_NOTICE =
            "the server hosting this file is temporarily unavailable for maintenance";
    private static final String DISABLED_NOTICE =
            "playback and downloads are disabled while maintenance is in progress";
    private static final String HIDDEN = "template, [hidden], [aria-hidden=true]";
    private static final LinkedHashMap<String, Long> EXPIRY = new LinkedHashMap<>();

    private BunkrMaintenance() {}

    static boolean isKnown(String url) {
        return remainingMillis(url) > 0;
    }

    static synchronized long remainingMillis(String url) {
        if (EXPIRY.isEmpty()) return 0;
        String key = fileKey(url);
        Long expiry = EXPIRY.get(key);
        if (expiry == null) return 0;
        long remaining = expiry - SystemClock.elapsedRealtime();
        if (remaining > 0) return remaining;
        EXPIRY.remove(key);
        return 0;
    }

    static synchronized void retry(String url) {
        EXPIRY.remove(fileKey(url));
    }

    static boolean isConfirmed(Throwable error) {
        while (error != null) {
            if (error instanceof UnavailableException) return true;
            Throwable cause = error.getCause();
            if (cause == error) return false;
            error = cause;
        }
        return false;
    }

    static boolean isFilePage(String url) {
        return fileKey(url) != null;
    }

    static boolean recordFilePage(String url, Document doc) {
        return recordFilePage(url, doc, false);
    }

    static boolean recordFilePage(String url, Document doc, boolean requireNotice) {
        String key = fileKey(url);
        if (key == null || !key.equals(fileKey(doc.location()))) return false;
        if (!hasVisibleNotice(doc.body()) && (requireNotice || !hasPlaceholderPlayer(doc))) return false;
        remember(key);
        return true;
    }

    private static synchronized void remember(String key) {
        // Reinsert fresh evidence so capacity eviction removes the oldest confirmation.
        EXPIRY.remove(key);
        EXPIRY.put(key, SystemClock.elapsedRealtime() + TTL_MS);
        if (EXPIRY.size() > MAX_ENTRIES) EXPIRY.remove(EXPIRY.keySet().iterator().next());
    }

    private static String fileKey(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String path = uri.getRawPath();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || path == null) return null;
            Matcher match = FILE_PATH.matcher(path);
            // Keep the case of the file slug. Bunkr page mirrors share the same path.
            if (!match.matches() || !BunkrRepository.isBunkrUrl(url)) return null;
            return match.group(1).toLowerCase(Locale.ROOT) + "/" + match.group(2);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean hasVisibleNotice(Element body) {
        if (!hasNoticeText(body)) return false;
        if (body.selectFirst(HIDDEN) == null) return true;
        Element visible = body.clone();
        visible.select(HIDDEN).remove();
        return hasNoticeText(visible);
    }

    private static boolean hasNoticeText(Element body) {
        String text = body.text().toLowerCase(Locale.ROOT);
        return text.contains(FILE_NOTICE) && text.contains(DISABLED_NOTICE);
    }

    private static boolean hasPlaceholderPlayer(Document doc) {
        boolean found = false;
        // Scripts may declare a maintenance fallback on healthy pages. Only player sources count.
        for (Element source : doc.select("video[src], video > source[src]")) {
            if (source.closest(HIDDEN) != null) continue;
            String url = source.absUrl("src");
            if (url.isEmpty()) continue;
            if (!BunkrRepository.isOfficialMaintenanceVideo(url)) return false;
            found = true;
        }
        return found;
    }

    static final class UnavailableException extends IOException {
        UnavailableException() {
            super("Bunkr file server is under maintenance");
        }
    }
}
