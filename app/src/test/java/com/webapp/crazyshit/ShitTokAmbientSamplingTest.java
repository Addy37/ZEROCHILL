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

    @Test public void gpuReflectionHasSmallSourceAndNoFrameSamplingTimer() {
        assertTrue(allowed(true, true, true, true, true, false,
                false, true, true, false));
        assertEquals(4, ShitTokAmbientSampling.DOWNSCALE);
        assertTrue(ShitTokAmbientSampling.BLUR_RADIUS_LOW_RES > 0);
        assertFalse(ShitTokGpuAmbientView.supported(30, true, true));
        assertFalse(ShitTokGpuAmbientView.supported(35, false, true));
        assertFalse(ShitTokGpuAmbientView.supported(35, true, false));
        assertTrue(ShitTokGpuAmbientView.supported(35, true, true));
    }

    @Test public void gpuReflectionNeverRunsOffscreenWhenPausedOrInFullscreen() {
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

    @Test public void repeatedFrameStallsTurnOffOptionalEffect() {
        long slow = ShitTokAmbientSampling.MAX_FRAME_RECORD_NS + 1;
        long missed = ShitTokAmbientSampling.MISSED_VSYNC_NS + 1;
        assertFalse(ShitTokAmbientSampling.shouldDisable(slow, 0, 0));
        assertFalse(ShitTokAmbientSampling.shouldDisable(slow, 0, 1));
        assertTrue(ShitTokAmbientSampling.shouldDisable(slow, 0, 2));
        assertTrue(ShitTokAmbientSampling.shouldDisable(0, missed, 2));
        assertFalse(ShitTokAmbientSampling.shouldDisable(10, 10, 2));
    }

    @Test public void portraitHorizontalBlurExtendsToTheSeekTrackWithoutPassingTheReservedGap() {
        assertEquals(41, ShitTokAmbientSampling.portraitBlurExtensionToSeek(44, 48, -13, 18, 0, 2));
        assertEquals(40, ShitTokAmbientSampling.portraitBlurExtensionToSeek(44, 48, -13, 18, 0, 4));
        assertEquals(0, ShitTokAmbientSampling.portraitBlurExtensionToSeek(0, 48, -13, 18, 0, 2));
        assertEquals(0, ShitTokAmbientSampling.portraitBlurExtensionToSeek(44, 0, -13, 18, 0, 2));
        assertTrue(ShitTokAmbientSampling.portraitBlurExtensionToSeek(44, 48, -13, 18, 0, 2) <= 44);
    }
}
