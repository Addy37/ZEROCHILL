package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Shared grouping for Library destinations. No network and no persisted data. */
final class LibraryBrowseGroups {
    static final String TODAY = "Today";
    static final String YESTERDAY = "Yesterday";
    static final String THIS_WEEK = "This week";
    static final String EARLIER = "Earlier";
    static final String[] DATE_ORDER = {TODAY, YESTERDAY, THIS_WEEK, EARLIER};

    private LibraryBrowseGroups() {}

    static String dateBucket(long epochMs, long nowMs) {
        if (epochMs <= 0L || nowMs <= 0L) return EARLIER;
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMs);
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(epochMs);
        if (sameDay(now, then)) return TODAY;
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(yesterday, then)) return YESTERDAY;
        long age = nowMs - epochMs;
        if (age >= 0L && age < 7L * 24L * 60L * 60L * 1000L) return THIS_WEEK;
        return EARLIER;
    }

    static String sourceLabel(String url) {
        String value = url == null ? "" : url.trim();
        if (value.isEmpty()) return "Other";
        if (BaddieHubRepository.isBaddieHubUrl(value)) return "BaddieHub";
        if (BunkrRepository.isBunkrUrl(value)) return "Bunkr";
        if (EromeRepository.isAlbumUrl(value) || hostContains(value, "erome.com")) return "Erome";
        if (hostContains(value, "xmegadrive.com")) return "XMegaDrive";
        if (PawchiveRepository.isDirectMedia(value) || hostContains(value, "fapello.com")
                || hostContains(value, "fapzone")) return "OnlyFap";
        return "Shows";
    }

    /** Titles that are only a storage id, hash, or filename token. */
    static boolean isRawIdentifier(String title) {
        if (title == null) return true;
        String value = title.trim();
        if (value.isEmpty()) return true;
        if (value.matches("\\d{4,}")) return true;
        if (value.matches("(?i)[0-9a-f]{8,}")) return true;
        if (value.matches("(?i)[0-9a-f-]{12,}")) return true;
        String compact = value.replaceAll("\\s+", "");
        if (!value.contains(" ") && compact.matches("[A-Za-z0-9._-]{12,}")
                && compact.replaceAll("(?i)[a-z._-]", "").length() >= 3) return true;
        return false;
    }

    static String readableTitle(NativeContentItem item, String fallback) {
        String candidate = item == null ? "" : OnlyFapVideoMetadata.displayTitle(item);
        if (isRawIdentifier(candidate)) candidate = fallback == null ? "" : fallback.trim();
        if (isRawIdentifier(candidate) && item != null && !isRawIdentifier(item.uploader)) {
            candidate = item.uploader.trim();
        }
        if (isRawIdentifier(candidate)) {
            return item != null && item.isImage() ? "Saved photo" : "Saved video";
        }
        return candidate;
    }

    static List<String> firstUrls(List<String> urls, int limit) {
        ArrayList<String> out = new ArrayList<>();
        if (urls == null || limit <= 0) return out;
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) continue;
            out.add(url.trim());
            if (out.size() >= limit) break;
        }
        return out;
    }

    static String letter(String title) {
        if (title == null) return "#";
        String value = title.trim();
        if (value.isEmpty()) return "#";
        int point = value.codePointAt(0);
        if (point >= 'a' && point <= 'z') point = point - 'a' + 'A';
        if (point >= 'A' && point <= 'Z') return String.valueOf((char) point);
        return "#";
    }

    private static boolean sameDay(Calendar left, Calendar right) {
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean hostContains(String url, String host) {
        return url.toLowerCase(Locale.US).contains(host.toLowerCase(Locale.US));
    }
}
