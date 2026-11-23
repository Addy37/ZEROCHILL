package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.*;

public class ShitTokAmbientSamplingTest {
    private boolean allowed(boolean selected, boolean active, boolean resumed,
            boolean playing, boolean horizontal, boolean fullscreen,
            boolean blocked, boolean textureAvailable, boolean supported,
            boolean failed) {
        return ShitTokAmbientSampling.shouldSample(selected, active, resumed,
                playing, horizontal, fullscreen, blocked, textureAvailable,
                supported, failed);
    }

    @Test public void selectedHorizontalVideoCanSampleWithoutAnotherDecoder() {
        assertTrue(allowed(true, true, true, true, true, false,
                false, true, true, false));
        assertEquals(250L, ShitTokAmbientSampling.FRAME_INTERVAL_MS);
        assertEquals(210, ShitTokAmbientSampling.CROSSFADE_MS);
        assertTrue(ShitTokAmbientSampling.CROSSFADE_MS
                < ShitTokAmbientSampling.FRAME_INTERVAL_MS);
        assertTrue(ShitTokAmbientSampling.FRAME_WIDTH <= 128);
        assertTrue(ShitTokAmbientSampling.FRAME_HEIGHT <= 72);
    }

    @Test public void liveAmbientNeverRunsOffscreenOrWhenFullscreenOrPaused() {
        boolean[] baseline = {true, true, true, true, true, false, false, true, true, false};
        for (int i : new int[]{0, 1, 2, 3, 4, 7, 8}) {
            boolean[] state = baseline.clone();
            state[i] = false;
            assertFalse("gate " + i, allowed(state[0], state[1], state[2], state[3],
                    state[4], state[5], state[6], state[7], state[8], state[9]));
        }
        for (int i : new int[]{5, 6, 9}) {
            boolean[] state = baseline.clone();
            state[i] = true;
            assertFalse("gate " + i, allowed(state[0], state[1], state[2], state[3],
                    state[4], state[5], state[6], state[7], state[8], state[9]));
        }
    }

    @Test public void repeatedSlowFrameCapturesTriggerFallback() {
        assertFalse(ShitTokAmbientSampling.tooSlow(25L, 0));
        assertFalse(ShitTokAmbientSampling.tooSlow(25L, 1));
        assertTrue(ShitTokAmbientSampling.tooSlow(25L, 2));
        assertFalse(ShitTokAmbientSampling.tooSlow(4L, 2));
    }
}
