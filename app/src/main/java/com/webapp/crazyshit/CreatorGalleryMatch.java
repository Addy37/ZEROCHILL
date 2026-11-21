package com.webapp.crazyshit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identity evidence used after a creator has been selected. */
final class CreatorGalleryMatch {
    static final int HANDLE = 0;
    static final int NAME = 1;
    static final int NO_MATCH = Integer.MAX_VALUE;

    private static final Pattern HANDLE_FORMAT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");
    private static final Pattern ALBUM_PART = Pattern.compile(
            "(?i)(?:[\\s._–—-]+(?:part|pt\\.?|volume|vol\\.?|set|disc)"
                    + "[\\s._–—-]*#?\\d{1,3}(?:\\s+of\\s+\\d{1,3})?"
                    + "|\\s*(?:\\(\\s*\\d{1,3}\\s*\\)|\\[\\s*\\d{1,3}\\s*\\]|#\\d{1,3}))$");

    private CreatorGalleryMatch() { }

    static String handle(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.startsWith("@")) raw = raw.substring(1);
        return HANDLE_FORMAT.matcher(raw).matches() ? raw.toLowerCase(Locale.ROOT) : "";
    }

    static boolean exactHandle(String expected, String candidate) {
        String wanted = handle(expected);
        return !wanted.isEmpty() && wanted.equals(handle(candidate));
    }

    static boolean exactName(String expected, String candidate) {
        if (CreatorIdentity.sameReviewedIdentity(expected, candidate)) return true;
        if (!handle(expected).isEmpty() && !handle(candidate).isEmpty()) {
            return exactHandle(expected, candidate);
        }
        return ShitTokFilteredIdentity.strong(expected, candidate);
    }

    static boolean albumMatches(String title, String identity) {
        if (title == null || title.isEmpty()) return false;
        String cleanTitle = title.trim();
        if (albumNameMatches(identity, cleanTitle)) return true;
        Matcher part = ALBUM_PART.matcher(cleanTitle);
        return part.find() && albumNameMatches(identity, cleanTitle.substring(0, part.start()));
    }

    private static boolean albumNameMatches(String identity, String title) {
        String expected = handle(identity);
        if (expected.indexOf('.') >= 0 || expected.indexOf('_') >= 0 || expected.indexOf('-') >= 0) {
            return exactHandle(expected, title) || CreatorIdentity.sameReviewedIdentity(expected, title);
        }
        return exactName(identity, title);
    }

    static int fapelloRank(FapelloRepository.Model model, String query) {
        if (model == null || !FapelloRepository.isModelUrl(model.url)) return NO_MATCH;
        if (exactHandle(query, model.username) || exactName(query, model.verifiedAlias)) return HANDLE;
        return exactName(query, model.name) ? NAME : NO_MATCH;
    }

    static boolean fapelloMatches(FapelloRepository.Model model, String query) {
        return fapelloRank(model, query) != NO_MATCH;
    }

    static int onlyHavenRank(OnlyHavenRepository.Creator creator, String query) {
        if (creator == null) return NO_MATCH;
        if (exactHandle(query, creator.username) || exactHandle(query, creator.id)) return HANDLE;
        return exactName(query, creator.name) ? NAME : NO_MATCH;
    }

    static boolean onlyHavenMatches(OnlyHavenRepository.Creator creator, String query) {
        return onlyHavenRank(creator, query) != NO_MATCH;
    }
}
