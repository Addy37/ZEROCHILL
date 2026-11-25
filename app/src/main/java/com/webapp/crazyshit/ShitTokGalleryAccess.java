package com.webapp.crazyshit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a ShitTok clip can already open, and any collection totals already on the item.
 * Counts come only from provider metadata. A loaded page is never treated as the whole album.
 */
final class ShitTokGalleryAccess {
    enum Kind { NONE, ALBUM, CREATOR }

    static final class Presentation {
        final Kind kind;
        final String label;
        final String contentDescription;

        Presentation(Kind kind, String label, String contentDescription) {
            this.kind = kind == null ? Kind.NONE : kind;
            this.label = label == null ? "" : label;
            this.contentDescription = contentDescription == null ? "" : contentDescription;
        }

        boolean visible() { return kind != Kind.NONE; }

        static final Presentation NONE = new Presentation(Kind.NONE, "", "");
    }

    private static final Pattern COUNT = Pattern.compile(
            "(?i)(\\d{1,3}(?:,\\d{3})+|\\d+)\\s+(videos?|photos?|pictures?|posts?)");

    private ShitTokGalleryAccess() {}

    static Presentation of(NativeContentItem clip, NativeContentItem creator) {
        if (clip == null) return Presentation.NONE;
        if (!ShitTokEromeGallery.albumUrl(clip).isEmpty()) {
            String counts = countsLabel(clip.galleryDetail);
            if (!hasAdditionalAlbumMedia(counts)) return Presentation.NONE;
            return present(Kind.ALBUM, "View album", "Open album", counts);
        }
        if (creator != null && !clean(creator.title).isEmpty()) {
            String counts = countsLabel(creator.galleryDetail);
            if (counts.isEmpty()) counts = countsLabel(creator.description);
            return present(Kind.CREATOR, "View creator", "Open creator", counts);
        }
        return Presentation.NONE;
    }

    /**
     * Turns an existing count phrase into pill text.
     * Empty when the text has no positive video, photo, or post total.
     */
    static String countsLabel(String text) {
        if (text == null || text.isEmpty()) return "";
        String videos = "";
        String photos = "";
        String posts = "";
        Matcher matcher = COUNT.matcher(text);
        while (matcher.find()) {
            String number = matcher.group(1);
            String unit = matcher.group(2).toLowerCase(Locale.US);
            if (unit.startsWith("video") && videos.isEmpty()) videos = number;
            else if ((unit.startsWith("photo") || unit.startsWith("picture")) && photos.isEmpty()) photos = number;
            else if (unit.startsWith("post") && posts.isEmpty()) posts = number;
        }
        if (positive(videos) || positive(photos)) {
            StringBuilder label = new StringBuilder();
            if (positive(videos)) label.append(phrase(videos, "video", "videos"));
            if (positive(videos) && positive(photos)) label.append(" · ");
            if (positive(photos)) label.append(phrase(photos, "photo", "photos"));
            return label.toString();
        }
        if (positive(posts)) return phrase(posts, "post", "posts");
        return "";
    }

    /** Counts already parsed from an Erome album page. Empty when that page is only the playing video. */
    static String albumDetail(java.util.List<NativeContentItem> media) {
        int videos = 0;
        int photos = 0;
        if (media != null) {
            for (NativeContentItem item : media) {
                if (item == null) continue;
                if (item.isImage()) photos++;
                else if (item.isVideo()) videos++;
            }
        }
        if (!hasAdditionalAlbumMedia(videos, photos)) return "";
        StringBuilder label = new StringBuilder();
        if (videos > 0) label.append(phrase(Integer.toString(videos), "video", "videos"));
        if (photos > 0) {
            if (label.length() > 0) label.append(" · ");
            label.append(phrase(Integer.toString(photos), "photo", "photos"));
        }
        return label.toString();
    }

    /**
     * Prefer the opened album page. Fall back to the listing card only when that card
     * already states more videos or photos. A lone playing video stays blank.
     */
    static String confirmedAlbumDetail(String pageDetail, String cardDescription) {
        String page = countsLabel(pageDetail);
        if (hasAdditionalAlbumMedia(page)) return page;
        String card = countsLabel(cardDescription);
        return hasAdditionalAlbumMedia(card) ? card : "";
    }

    static boolean hasAdditionalAlbumMedia(String counts) {
        int[] totals = totals(counts);
        return hasAdditionalAlbumMedia(totals[0], totals[1]);
    }

    private static boolean hasAdditionalAlbumMedia(int videos, int photos) {
        return videos > 1 || photos > 0;
    }

    private static int[] totals(String text) {
        int videos = 0;
        int photos = 0;
        if (text == null || text.isEmpty()) return new int[] {0, 0};
        Matcher matcher = COUNT.matcher(text);
        while (matcher.find()) {
            int number = number(matcher.group(1));
            String unit = matcher.group(2).toLowerCase(Locale.US);
            if (unit.startsWith("video") && videos == 0) videos = number;
            else if ((unit.startsWith("photo") || unit.startsWith("picture")) && photos == 0) photos = number;
        }
        return new int[] {videos, photos};
    }

    private static int number(String raw) {
        try {
            return Integer.parseInt(raw.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Presentation present(Kind kind, String fallback, String action, String counts) {
        String label = counts == null || counts.isEmpty() ? fallback : counts;
        String spoken = label.equals(fallback) ? fallback : label + ". " + action;
        return new Presentation(kind, label, spoken + ". Swipe right.");
    }

    private static boolean positive(String number) {
        if (number == null || number.isEmpty()) return false;
        try {
            return Long.parseLong(number.replace(",", "")) > 0L;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String phrase(String number, String singular, String plural) {
        return number + " " + ("1".equals(number) ? singular : plural);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
