package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CreatorGalleryMotionTest {
    @Test
    public void overlayScaleShrinksAsGalleryDensityIncreases() {
        assertEquals(1f, BunkrGalleryAdapter.overlayScaleForColumns(2), 0.001f);
        assertEquals(0.82f, BunkrGalleryAdapter.overlayScaleForColumns(3), 0.001f);
        assertEquals(0.68f, BunkrGalleryAdapter.overlayScaleForColumns(4), 0.001f);
        assertEquals(0.58f, BunkrGalleryAdapter.overlayScaleForColumns(5), 0.001f);
        assertTrue(
                BunkrGalleryAdapter.overlayScaleForColumns(7) <
                        BunkrGalleryAdapter.overlayScaleForColumns(5)
        );
    }

    @Test
    public void puzzleSettleRipplesOutwardFromPinchPoint() {
        long near = NativeFeedBrowserActivity.creatorGridSettleDelay(20f, 1000f);
        long middle = NativeFeedBrowserActivity.creatorGridSettleDelay(400f, 1000f);
        long far = NativeFeedBrowserActivity.creatorGridSettleDelay(1000f, 1000f);

        assertTrue(near < middle);
        assertTrue(middle < far);
        assertTrue(far <= 85L);
    }

    @Test
    public void puzzleSettleDelayHandlesDegenerateDistance() {
        assertEquals(0L, NativeFeedBrowserActivity.creatorGridSettleDelay(0f, 1000f));
        assertEquals(0L, NativeFeedBrowserActivity.creatorGridSettleDelay(100f, 0f));
    }
}
