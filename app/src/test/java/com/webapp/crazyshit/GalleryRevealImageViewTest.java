package com.webapp.crazyshit;

import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class GalleryRevealImageViewTest {
    @Test public void revealWaitsForFastScrollButAllowsSlowDrag() {
        assertFalse(GalleryRevealImageView.canStart(RecyclerView.SCROLL_STATE_SETTLING, 1000L));
        assertFalse(GalleryRevealImageView.canStart(RecyclerView.SCROLL_STATE_DRAGGING, 50L));
        assertTrue(GalleryRevealImageView.canStart(RecyclerView.SCROLL_STATE_DRAGGING, 110L));
        assertTrue(GalleryRevealImageView.canStart(RecyclerView.SCROLL_STATE_IDLE, 110L));
    }

    @Test public void workloadFollowsVisibleTilesAndGridDensity() {
        assertEquals(8, GalleryWorkload.revealCapacity(2, 8, false));
        assertEquals(20, GalleryWorkload.revealCapacity(5, 18, false));
        assertEquals(32, GalleryWorkload.revealCapacity(5, 32, false));
        assertEquals(16, GalleryWorkload.revealCapacity(5, 32, true));
        assertTrue(GalleryWorkload.staggerMs(5, false) < GalleryWorkload.staggerMs(2, false));
        assertEquals(20, GalleryWorkload.preparationWindow(4, 12));
        assertEquals(6, GalleryWorkload.preparationConcurrency(5, false));
        assertEquals(3, GalleryWorkload.preparationConcurrency(5, true));
    }

    @Test public void sustainedFramePressureTemporarilyReducesWork() {
        GalleryWorkload.FramePressure pressure = new GalleryWorkload.FramePressure();
        pressure.sample(10_000_000L, 10L);
        pressure.sample(50_000_000L, 50L);
        pressure.sample(90_000_000L, 90L);
        assertFalse(pressure.constrained(90L));
        pressure.sample(130_000_000L, 130L);
        assertTrue(pressure.constrained(131L));
        assertFalse(pressure.constrained(1630L));
    }

    @Test public void uniformRevealTiming() {
        assertEquals(400L, GalleryRevealImageView.DURATION_MS);
        assertEquals(50L, GalleryRevealImageView.STAGGER_MS);
    }

    @Test public void softEdgeTravelsFromAboveTheTileToBelowIt() {
        assertEquals(-65f, GalleryRevealImageView.front(0f, 100f, 65f), .001f);
        assertEquals(100f, GalleryRevealImageView.front(1f, 100f, 65f), .001f);
        assertEquals(-36f, GalleryRevealImageView.front(0f, 100f, 36f), .001f);
        assertEquals(32f, GalleryRevealImageView.front(.5f, 100f, 36f), .001f);
        assertEquals(100f, GalleryRevealImageView.front(1f, 100f, 36f), .001f);
    }
}
