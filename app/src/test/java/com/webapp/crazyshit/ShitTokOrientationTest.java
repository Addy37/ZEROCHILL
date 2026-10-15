package com.webapp.crazyshit;

import static org.junit.Assert.*;
import org.junit.Test;

public class ShitTokOrientationTest {
    @Test public void acceptsOnlyFinitePortraitOrSquare() {
        assertTrue(ShitTokOrientation.eligible(9f / 16));
        assertTrue(ShitTokOrientation.eligible(1f));
        for (float aspect : new float[]{0f, -1f, 1.00001f, 16f / 9, Float.NaN, Float.POSITIVE_INFINITY})
            assertFalse(ShitTokOrientation.eligible(aspect));
    }
    @Test public void appliesRotationAndNonSquarePixelRatio() {
        assertEquals(9f / 16, ShitTokOrientation.effectiveAspect(1920, 1080, 1, 90), .00001f);
        assertEquals(9f / 16, ShitTokOrientation.effectiveAspect(1920, 1080, 1, -90), .00001f);
        assertEquals(1.125f, ShitTokOrientation.effectiveAspect(720, 1280, 2, 0), .00001f);
        assertEquals(1f / 1.125f, ShitTokOrientation.effectiveAspect(720, 1280, 2, 270), .00001f);
        assertEquals(1f, ShitTokOrientation.effectiveAspect(1080, 1080, 1, 180), 0f);
    }
    @Test public void everyAdaptiveVideoVariantMustBeKnownAndEligible() {
        assertTrue(ShitTokOrientation.eligible(ShitTokOrientation.conservativeAspect(
                java.util.Arrays.asList(.5f, .75f, 1f))));
        assertFalse(ShitTokOrientation.eligible(ShitTokOrientation.conservativeAspect(
                java.util.Arrays.asList(.5f, 1.2f))));
        assertFalse(ShitTokOrientation.eligible(ShitTokOrientation.conservativeAspect(
                java.util.Arrays.asList(.5f, 0f))));
        assertFalse(ShitTokOrientation.eligible(ShitTokOrientation.conservativeAspect(
                java.util.Arrays.asList(.5f, Float.NaN))));
        assertEquals(0f, ShitTokOrientation.conservativeAspect(java.util.Collections.emptyList()), 0f);
    }
    @Test public void unknownOrInvalidGeometryRemainsUnknown() {
        assertEquals(0f, ShitTokOrientation.effectiveAspect(0, 1080, 1, 0), 0f);
        assertEquals(0f, ShitTokOrientation.effectiveAspect(1920, -1, 1, 0), 0f);
        assertEquals(0f, ShitTokOrientation.effectiveAspect(1920, 1080, Float.NaN, 0), 0f);
        assertEquals(0f, ShitTokOrientation.effectiveAspect(1920, 1080, 1, 45), 0f);
    }
}
