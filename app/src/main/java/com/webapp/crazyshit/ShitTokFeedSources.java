package com.webapp.crazyshit;

import java.util.Locale;

/** Final admission boundary for the five owner-approved ShitTok source lineups. */
final class ShitTokFeedSources {
    private ShitTokFeedSources() {}

    static boolean allows(ShitTokCategoryStrip.Tab tab, String rawSource) {
        if (tab == null || rawSource == null) return false;
        String source = rawSource.toLowerCase(Locale.US);
        switch (tab) {
            case CHAOS:
                return source.contains("crazyshit") || source.equals("kaotic")
                        || source.equals("efukt") || source.equals("shit-show");
            case LADIES:
                return creatorSource(source) || source.equals("baddiehub")
                        || source.equals("shit-show");
            case GROUPS:
                return creatorSource(source) || source.equals("baddiehub");
            case GUYS:
            case TRANS:
                return creatorSource(source);
            default:
                return false;
        }
    }

    private static boolean creatorSource(String source) {
        return source.equals("onlyhaven") || source.contains("cum.st")
                || source.equals("fapello") || source.endsWith("fapello.com")
                || source.equals("bunkr") || source.contains("bunkr");
    }
}
