package com.webapp.crazyshit;

import android.app.Application;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Decoder-free end-to-end pool tests for retaining the last category clip.
 * Test the real ShitTokPlaybackOwner with its existing 3-slot PlayerPool.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokRetainedPlayerReturnTest {
    @Test public void quickABAReturnKeepsPreparedVideoAndPositionWithoutNewMediaSource() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            h.select(1); h.prepare(1); h.attach(1);
            ShitTokPlayerPoolTest.Engine first = h.engine(1);
            ExoPlayer retained = first.player;
            first.position = 37_000L;
            int initialPrepares = first.prepares, initialSources = first.sourceAssignments;
            h.owner.parkSelectedForCategoryReturn(ShitTokPlayerPoolTest.id(1));
            assertFalse(first.play);
            assertEquals(0f, first.volume, 0f);
            assertTrue(h.owner.hasPreparedReturnLease(ShitTokPlayerPoolTest.id(1)));

            h.owner.permission(false, false);
            h.owner.select(ShitTokPlayerPoolTest.id(10), ShitTokPlayerPoolTest.id(11), null, true);
            h.owner.permission(true, false);
            h.prepare(10); h.attach(10);
            assertSame(retained, h.owner.player(ShitTokPlayerPoolTest.id(1)));
            assertEquals(3, h.engines.size());
            assertEquals(1, h.audible());

            // Returning to category A reuses the original engine while B borrows its slot.
            h.owner.parkSelectedForCategoryReturn(ShitTokPlayerPoolTest.id(10));
            h.owner.permission(false, false);
            h.owner.select(ShitTokPlayerPoolTest.id(1), ShitTokPlayerPoolTest.id(2),
                    ShitTokPlayerPoolTest.id(0), true);
            h.owner.permission(true, false);
            h.attach(1);
            h.prepare(1); // Same resolved URL must remain a no-op.
            assertSame(retained, h.owner.player(ShitTokPlayerPoolTest.id(1)));
            assertEquals(initialPrepares, first.prepares);
            assertEquals(initialSources, first.sourceAssignments);
            assertEquals(0, first.stops);
            assertEquals(37_000L, first.position);
            assertTrue(h.owner.summary().contains("parked=true"));
            assertEquals(3, h.engines.size());
            assertEquals(1, h.audible());
        }
    }

    @Test public void verticalSelectionDropsParkedLeaseAndRestoresBackwardNeighbor() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            h.select(1); h.prepare(1); h.attach(1);
            h.owner.parkSelectedForCategoryReturn(ShitTokPlayerPoolTest.id(1));
            h.owner.select(ShitTokPlayerPoolTest.id(10), ShitTokPlayerPoolTest.id(11), null, true);
            h.prepare(10); h.attach(10);
            assertNotNull(h.owner.player(ShitTokPlayerPoolTest.id(1)));
            assertNull("Previous vertical neighbor yields to parked tab", h.owner.player(ShitTokPlayerPoolTest.id(0)));

            h.owner.select(ShitTokPlayerPoolTest.id(11), ShitTokPlayerPoolTest.id(12),
                    ShitTokPlayerPoolTest.id(10), true);
            assertNull("A real swipe must not hold old tab's decoder", h.owner.player(ShitTokPlayerPoolTest.id(1)));
            assertTrue(h.retired.contains(ShitTokPlayerPoolTest.id(1)));
            assertNotNull(h.owner.player(ShitTokPlayerPoolTest.id(10)));
            assertNotNull(h.owner.player(ShitTokPlayerPoolTest.id(11)));
            assertNotNull(h.owner.player(ShitTokPlayerPoolTest.id(12)));
            assertTrue(h.owner.summary().contains("parked=false"));
            assertEquals(3, h.engines.size());
        }
    }

    @Test public void unpreparedOrErroredVideoIsNeverParked() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            h.select(1); h.prepare(1);
            h.engine(1).state = Player.STATE_BUFFERING;
            h.owner.parkSelectedForCategoryReturn(ShitTokPlayerPoolTest.id(1));
            assertFalse(h.owner.summary().contains("parked=true"));
            h.owner.select(ShitTokPlayerPoolTest.id(10), ShitTokPlayerPoolTest.id(11), null, true);
            assertNull(h.owner.player(ShitTokPlayerPoolTest.id(1)));
            assertEquals(3, h.engines.size());
        }
    }

    @Test public void userPauseAndMuteRemainIntactAfterRetainedReturn() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            h.select(1); h.prepare(1); h.attach(1);
            h.owner.setUserPaused(ShitTokPlayerPoolTest.id(1), true);
            h.owner.parkSelectedForCategoryReturn(ShitTokPlayerPoolTest.id(1));
            h.owner.permission(false, true);
            h.owner.select(ShitTokPlayerPoolTest.id(10), null, null, false);
            h.owner.permission(true, true);
            h.prepare(10); h.attach(10);
            h.owner.parkSelectedForCategoryReturn(ShitTokPlayerPoolTest.id(10));
            h.owner.permission(false, true);
            h.owner.select(ShitTokPlayerPoolTest.id(1), null, null, false);
            h.owner.permission(true, true);
            h.attach(1);
            assertTrue(h.owner.isUserPaused(ShitTokPlayerPoolTest.id(1)));
            assertFalse(h.engine(1).play);
            assertEquals(0f, h.engine(1).volume, 0f);
            assertEquals(0, h.audible());
            assertEquals(3, h.engines.size());
        }
    }

    @Test public void leavingFeedOrCoveringSearchClearsRetainedBackgroundEngine() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            h.select(1); h.prepare(1);
            h.owner.parkSelectedForCategoryReturn(ShitTokPlayerPoolTest.id(1));
            h.owner.select(ShitTokPlayerPoolTest.id(10), null, null, false);
            h.owner.setSearchCovered(true);
            assertNull(h.owner.player(ShitTokPlayerPoolTest.id(1)));
            assertTrue(h.owner.summary().contains("parked=false"));
            h.owner.setSearchCovered(false);
            h.owner.release();
            assertTrue(h.owner.summary().contains("acquired=0"));
        }
    }
}
