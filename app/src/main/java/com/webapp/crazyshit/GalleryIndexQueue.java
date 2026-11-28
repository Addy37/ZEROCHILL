package com.webapp.crazyshit;

/** Pure scheduling and terminal-state policy used by the persisted WorkManager queue. */
final class GalleryIndexQueue {
    static final int MAX_ACTIVE = 2;
    static final int MAX_ITEMS = 10000;
    static final int MAX_EMPTY_BATCHES = 6;

    private GalleryIndexQueue() { }

    static int chooseLane(int first, int second) {
        return first <= second ? 0 : 1;
    }

    static boolean active(String status) {
        return "Waiting".equals(status) || "Fetching".equals(status)
                || "Cancelling".equals(status);
    }

    static String transition(String previous, String requested) {
        return "Cancelling".equals(previous) || "Cancelled".equals(previous)
                ? "Cancelled" : requested;
    }

    static boolean partial(int items, int emptyBatches, boolean sourceIncomplete) {
        return items >= MAX_ITEMS || emptyBatches >= MAX_EMPTY_BATCHES || sourceIncomplete;
    }
}
