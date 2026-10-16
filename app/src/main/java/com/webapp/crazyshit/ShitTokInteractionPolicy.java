package com.webapp.crazyshit;

/** Interaction-only values. Independent of playback, inventory and preload policies. */
final class ShitTokInteractionPolicy {
    static final long LONG_VIDEO_MS = 60_000L;
    static final long PREVIEW_INTERVAL_MS = 100L;
    static final long PREVIEW_TIMEOUT_MS = 1_500L;
    static final int PREVIEW_CACHE_SIZE = 4;
    static final int PREVIEW_WIDTH_PX = 180, PREVIEW_HEIGHT_PX = 320;

    private ShitTokInteractionPolicy() {}

    static boolean persistentProgress(long durationMs) { return durationMs >= LONG_VIDEO_MS; }

    static long targetTime(long durationMs, int progress, int max) {
        if (durationMs <= 0L || max <= 0) return 0L;
        long bounded = Math.max(0, Math.min(max, progress));
        return (durationMs / max) * bounded + ((durationMs % max) * bounded) / max;
    }

    static float previewLeft(float centerX, float previewWidth, float containerWidth, float margin) {
        float right = Math.max(margin, containerWidth - margin - previewWidth);
        return Math.max(margin, Math.min(right, centerX - previewWidth / 2f));
    }
}
