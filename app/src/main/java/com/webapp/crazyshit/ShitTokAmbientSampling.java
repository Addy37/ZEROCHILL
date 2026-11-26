package com.webapp.crazyshit;

/**
 * Admission and performance limits for the ShitTok GPU ambient reflection.
 * No CPU bitmap screenshots, timed sampling loop or crossfades remain.
 */
final class ShitTokAmbientSampling {
    static final int DOWNSCALE = 4;
    static final int BLUR_RADIUS_LOW_RES = 6;
    static final long MAX_FRAME_RECORD_NS = 12_000_000L;
    static final long MISSED_VSYNC_NS = 80_000_000L;
    static final int MAX_SLOW_FRAMES = 3;

    private ShitTokAmbientSampling() { }

    static boolean shouldSample(boolean selected, boolean active, boolean resumed,
            boolean playing, boolean horizontal, boolean fullscreen,
            boolean interactionBlocked, boolean textureAvailable, boolean supported,
            boolean failed) {
        return selected && active && resumed && playing && horizontal && !fullscreen
                && !interactionBlocked && textureAvailable && supported && !failed;
    }

    static boolean shouldDisable(long recordNs, long frameGapNs, int previousSlowFrames) {
        boolean slow = recordNs > MAX_FRAME_RECORD_NS ||
                frameGapNs > MISSED_VSYNC_NS;
        return slow && previousSlowFrames + 1 >= MAX_SLOW_FRAMES;
    }

    /**
     * Extra pixels the portrait horizontal blur needs so its bottom meets the
     * seek track. The fitted video viewport stays put; this only fills the gap
     * under it. {@code progressBottomMargin} uses layout-margin sign, so a
     * negative value pulls the row downward.
     */
    static int portraitBlurExtensionToSeek(
            int reservedMediaBottom,
            int progressHeight,
            int progressBottomMargin,
            int seekPaddingTop,
            int seekPaddingBottom,
            int trackHeight
    ) {
        if (reservedMediaBottom <= 0 || progressHeight <= 0) return 0;
        int content = progressHeight - Math.max(0, seekPaddingTop) - Math.max(0, seekPaddingBottom);
        if (content <= 0) return 0;
        int track = Math.max(1, Math.min(Math.max(trackHeight, 1), content));
        int trackInset = Math.max(0, seekPaddingTop) + (content - track) / 2;
        int extension = reservedMediaBottom - progressBottomMargin - progressHeight + trackInset;
        if (extension <= 0) return 0;
        return Math.min(extension, reservedMediaBottom);
    }
}
