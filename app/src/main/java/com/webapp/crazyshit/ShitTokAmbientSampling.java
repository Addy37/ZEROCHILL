package com.webapp.crazyshit;

/**
 * Conservative frame sampling for the optional ShitTok horizontal-video ambient.
 * Samples only the selected, playing TextureView. All other states keep the
 * existing static thumbnail backdrop without extra video decoders or requests.
 */
final class ShitTokAmbientSampling {
    static final long FRAME_INTERVAL_MS = 500L;
    static final int FRAME_WIDTH = 96;
    static final int FRAME_HEIGHT = 54;
    static final long SLOW_SAMPLE_MS = 24L;
    static final int MAX_SLOW_SAMPLES = 3;

    private ShitTokAmbientSampling() { }

    static boolean shouldSample(boolean selected, boolean active, boolean resumed,
            boolean playing, boolean horizontal, boolean fullscreen,
            boolean interactionBlocked, boolean textureAvailable, boolean supported,
            boolean failed) {
        return selected && active && resumed && playing && horizontal && !fullscreen
                && !interactionBlocked && textureAvailable && supported && !failed;
    }

    static boolean tooSlow(long durationMs, int previousSlowSamples) {
        return durationMs > SLOW_SAMPLE_MS && previousSlowSamples + 1 >= MAX_SLOW_SAMPLES;
    }
}
