package com.webapp.crazyshit;

/** Give a known cached return clip a short head start over optional inventory discovery. */
final class ShitTokReturnBufferPriority {
    static final long DISCOVERY_GRACE_MS = 650L;
    private static final int MIN_UPCOMING = 2;

    static boolean shouldDeferDiscovery(boolean cachedSelectedStream, int upcomingCount) {
        return cachedSelectedStream && upcomingCount >= MIN_UPCOMING;
    }

    private ShitTokReturnBufferPriority() {}
}
