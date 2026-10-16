package com.webapp.crazyshit;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShitTokInteractionPolicyTest {
    @Test public void longVideoBoundaryAndUnknownDuration() {
        assertFalse(ShitTokInteractionPolicy.persistentProgress(-9223372036854775807L));
        assertFalse(ShitTokInteractionPolicy.persistentProgress(0));
        assertFalse(ShitTokInteractionPolicy.persistentProgress(59_999));
        assertTrue(ShitTokInteractionPolicy.persistentProgress(60_000));
        assertTrue(ShitTokInteractionPolicy.persistentProgress(3_600_000));
    }
    @Test public void seekMappingClampsAndDoesNotOverflowLongDurations() {
        assertEquals(47_040, ShitTokInteractionPolicy.targetTime(140_000, 336, 1000));
        assertEquals(0, ShitTokInteractionPolicy.targetTime(140_000, -4, 1000));
        assertEquals(140_000, ShitTokInteractionPolicy.targetTime(140_000, 2000, 1000));
        assertEquals(0, ShitTokInteractionPolicy.targetTime(-1, 500, 1000));
        assertEquals(0, ShitTokInteractionPolicy.targetTime(100, 500, 0));
        assertEquals(Long.MAX_VALUE / 2, ShitTokInteractionPolicy.targetTime(Long.MAX_VALUE, 500, 1000));
    }
    @Test public void previewStaysInsideBothEdgesAndTracksMiddle() {
        assertEquals(8f, ShitTokInteractionPolicy.previewLeft(0, 152, 400, 8), 0f);
        assertEquals(240f, ShitTokInteractionPolicy.previewLeft(400, 152, 400, 8), 0f);
        assertEquals(124f, ShitTokInteractionPolicy.previewLeft(200, 152, 400, 8), 0f);
        assertEquals(8f, ShitTokInteractionPolicy.previewLeft(30, 44, 60, 8), 0f);
    }
}
