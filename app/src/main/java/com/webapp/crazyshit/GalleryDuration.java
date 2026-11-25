package com.webapp.crazyshit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Clock labels already stored on a gallery item. Never fetches duration. */
final class GalleryDuration {
    private static final Pattern HOURS = Pattern.compile("^(\\d{1,2}):([0-5]\\d):([0-5]\\d)$");
    private static final Pattern MINUTES = Pattern.compile("^(\\d{1,3}):([0-5]\\d)$");

    private GalleryDuration() {}

    /** YouTube-style label, or empty when this item has no duration metadata. */
    static String label(NativeContentItem item) {
        if (item == null || !item.isVideo()) return "";
        return format(item.description);
    }

    /** Accepts MM:SS and H:MM:SS. Anything else, including captions, is not a duration. */
    static String format(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        Matcher hours = HOURS.matcher(value);
        if (hours.matches()) {
            int hour = Integer.parseInt(hours.group(1));
            return hour + ":" + hours.group(2) + ":" + hours.group(3);
        }
        Matcher minutes = MINUTES.matcher(value);
        if (minutes.matches()) {
            int minute = Integer.parseInt(minutes.group(1));
            return minute + ":" + minutes.group(2);
        }
        return "";
    }
}
