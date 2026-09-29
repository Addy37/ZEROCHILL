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
    public void morphScalePreservesVisualSizeAcrossGridThreshold() {
        assertEquals(
                0.80f,
                NativeFeedBrowserActivity.creatorGridMorphScale(80f, 100f),
                0.001f
        );
        assertEquals(
                1.20f,
                NativeFeedBrowserActivity.creatorGridMorphScale(120f, 100f),
                0.001f
        );
        assertEquals(
                1f,
                NativeFeedBrowserActivity.creatorGridMorphScale(100f, 100f),
                0.001f
        );
    }

    @Test
    public void morphScaleClampsExtremeOrInvalidGeometry() {
        assertEquals(
                0.76f,
                NativeFeedBrowserActivity.creatorGridMorphScale(20f, 100f),
                0.001f
        );
        assertEquals(
                1.24f,
                NativeFeedBrowserActivity.creatorGridMorphScale(200f, 100f),
                0.001f
        );
        assertEquals(
                1f,
                NativeFeedBrowserActivity.creatorGridMorphScale(0f, 100f),
                0.001f
        );
        assertEquals(
                1f,
                NativeFeedBrowserActivity.creatorGridMorphScale(100f, 0f),
                0.001f
        );
    }
}
