package com.webapp.crazyshit;

/** One selected clip's playback position for the lifetime of a ShitTok view. */
final class ShitTokSessionResume {
    private String url;
    private long positionMs;

    void remember(String clipUrl, long position, long duration) {
        if (clipUrl == null || clipUrl.isEmpty() || position <= 0L ||
                (duration > 0L && position >= duration - 500L)) {
            clear();
            return;
        }
        url = clipUrl;
        positionMs = position;
    }

    long positionFor(String clipUrl) {
        return url != null && url.equals(clipUrl) ? positionMs : 0L;
    }

    void clear() {
        url = null;
        positionMs = 0L;
    }
}
