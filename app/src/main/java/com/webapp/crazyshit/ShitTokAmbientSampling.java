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
}
