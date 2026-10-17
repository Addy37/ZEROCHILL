package com.addy37.crazyshitadmin;

/** Presentation policy for compact analytics ranking cards. */
final class AnalyticsListPolicy {
    static final int COLLAPSED_ROWS = 5;

    private AnalyticsListPolicy() {}

    static boolean shouldShow(int index, boolean expanded) {
        return expanded || index < COLLAPSED_ROWS;
    }

    static boolean needsToggle(int count) {
        return count > COLLAPSED_ROWS;
    }

    static String toggleLabel(int count, boolean expanded) {
        return expanded ? "Show less ↑" : "View all " + count + " ↓";
    }
}
