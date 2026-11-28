package com.webapp.crazyshit;

/** Pure scheduling and terminal-state policy used by the persisted WorkManager queue. */
final class GalleryIndexQueue {
    static final int MAX_ACTIVE = 2;
    static final int MAX_ITEMS = 10000;
    static final int MAX_EMPTY_BATCHES = 6;

    private GalleryIndexQueue() { }

    // Android thermal status: moderate=2, severe=3, critical=4.
    static int previewSlots(boolean interactive, boolean playback, int thermal) {
        if (thermal >= 4) return 0;
        if (playback || thermal >= 3) return 1;
        if (interactive || thermal >= 2) return 2;
        return 4;
    }

    static long pagePause(boolean playback, int thermal) {
        return thermal >= 3 ? 1500L : playback ? 750L : 0L;
    }

    static int chooseLane(int first, int second) {
        return first <= second ? 0 : 1;
    }

    static boolean active(String status) {
        return "Waiting".equals(status) || "Fetching".equals(status) || "Preparing thumbnails".equals(status)
                || "Cancelling".equals(status);
    }

    static String transition(String previous, String requested) {
        return "Cancelling".equals(previous) || "Cancelled".equals(previous)
                ? "Cancelled" : requested;
    }

    static int stagnantBatches(int previous, int oldCount, int newCount,
            String oldCursor, String newCursor) {
        return oldCount == newCount && java.util.Objects.equals(oldCursor, newCursor)
                ? previous + 1 : 0;
    }

    static boolean stopIndexing(int items, int stagnantBatches) {
        return items >= MAX_ITEMS || stagnantBatches >= MAX_EMPTY_BATCHES;
    }

    static boolean partial(int items, boolean sourceIncomplete) {
        return items >= MAX_ITEMS || sourceIncomplete;
    }
}
