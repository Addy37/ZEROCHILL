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

    @Test public void softEdgeTravelsFromAboveTheTileToBelowIt() {
        assertEquals(-36f, GalleryRevealImageView.front(0f, 100f, 36f), .001f);
        assertEquals(32f, GalleryRevealImageView.front(.5f, 100f, 36f), .001f);
        assertEquals(100f, GalleryRevealImageView.front(1f, 100f, 36f), .001f);
    }
}
