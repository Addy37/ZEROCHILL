package com.webapp.crazyshit;

import java.util.Locale;

/** Numbering is relative to the full source collection, never the visible page. */
final class ShowsEpisodePresentation {
    private ShowsEpisodePresentation() { }

    static String label(int offset, int index) {
        return String.format(Locale.US, "EPISODE %02d", Math.max(0, offset) + Math.max(0, index) + 1);
    }
}
