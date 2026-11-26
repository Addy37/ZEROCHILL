package com.webapp.crazyshit;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShowsContinueFrameCropTest {
    @Test public void verticalVideoPillarboxFillsByDroppingSymmetricBlackBars() {
        int width = 160;
        int height = 90;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xFF000000);
        int left = 55;
        int right = 105;
        for (int y = 0; y < height; y++) {
            for (int x = left; x < right; x++) pixels[y * width + x] = 0xFFCC8844;
        }
        int[] region = ShowsContinueFrameCrop.region(pixels, width, height);
        assertEquals(left, region[0]);
        assertEquals(0, region[1]);
        assertEquals(right - left, region[2]);
        assertEquals(height, region[3]);
    }

    @Test public void fullBleedAndOneSidedDarknessStayUntouched() {
        int width = 160;
        int height = 90;
        int[] full = new int[width * height];
        Arrays.fill(full, 0xFF336699);
        assertNull(ShowsContinueFrameCrop.region(full, width, height));

        int[] oneSide = full.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 40; x++) oneSide[y * width + x] = 0xFF000000;
        }
        assertNull(ShowsContinueFrameCrop.region(oneSide, width, height));
    }
}
