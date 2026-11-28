package com.webapp.crazyshit;

/** Standalone checks: no Android runtime or external dependencies required. */
public final class GalleryIndexQueueChecks {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    public static void main(String[] args) {
        check(GalleryIndexQueue.MAX_ACTIVE == 2);
        check(GalleryIndexQueue.chooseLane(0, 0) == 0);
        check(GalleryIndexQueue.chooseLane(1, 0) == 1);
        check(GalleryIndexQueue.chooseLane(1, 1) == 0);
        check(GalleryIndexQueue.chooseLane(2, 1) == 1);
        check(GalleryIndexQueue.active("Waiting"));
        check(GalleryIndexQueue.active("Fetching"));
        check(GalleryIndexQueue.active("Preparing thumbnails"));
        check(!GalleryIndexQueue.active("Ready"));
        check(GalleryIndexQueue.active("Cancelling"));
        check(!GalleryIndexQueue.active("Cancelled"));
        check(!GalleryIndexQueue.active("Indexed"));
        check(!GalleryIndexQueue.active("Partial"));
        check(!GalleryIndexQueue.active("Failed"));
        check(GalleryIndexQueue.transition("Cancelling", "Indexed").equals("Cancelled"));
        check(GalleryIndexQueue.transition("Cancelled", "Fetching").equals("Cancelled"));
        check(GalleryIndexQueue.transition("Fetching", "Indexed").equals("Indexed"));
        check(!GalleryIndexQueue.stopIndexing(544, 5));
        check(GalleryIndexQueue.stopIndexing(544, 6));
        check(GalleryIndexQueue.stopIndexing(10000, 0));
        check(!GalleryIndexQueue.partial(544, false));
        check(GalleryIndexQueue.partial(544, true));
        check(GalleryIndexQueue.partial(10000, false));
        check(GalleryIndexQueue.stagnantBatches(5, 544, 544, "page1", "page2") == 0);
        check(GalleryIndexQueue.stagnantBatches(5, 544, 544, "page2", "page2") == 6);
        check(GalleryIndexQueue.stagnantBatches(5, 544, 600, "page2", "page2") == 0);
        System.out.println("GalleryIndexQueue checks passed");
    }
}
