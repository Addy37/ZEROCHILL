package com.webapp.crazyshit;

import android.app.Application;
import androidx.media3.common.Player;
import androidx.media3.common.FlagSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import java.time.Duration;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokBufferOwnerTest {
    private void state(ShitTokPlayerPoolTest.Engine e, int value) {
        e.state = value;
        for (Player.Listener listener : new ArrayList<>(e.listeners)) {
            listener.onPlaybackStateChanged(value);
            listener.onEvents(e.player, new Player.Events(new FlagSet.Builder().add(Player.EVENT_PLAYBACK_STATE_CHANGED).build()));
        }
    }
    @Test public void adaptiveTracksRetainNormalDowngradeSelection() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            h.select(0);
            androidx.media3.common.TrackSelectionParameters p = h.engine(0).parameters;
            assertFalse(p.forceHighestSupportedBitrate);
            assertFalse(p.forceLowestBitrate);
            assertEquals(0, p.minVideoBitrate);
            assertEquals(8_000_000, p.maxVideoBitrate);
        }
    }
    @Test public void starvationDefersNeighborSourceAndRecoveryRestoresItWithoutResettingCurrent() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            h.select(0); h.prepare(0); h.attach(0);
            ShitTokPlayerPoolTest.Engine current = h.engine(0), neighbor = h.engine(1);
            state(current, Player.STATE_READY);
            int assignments = current.sourceAssignments, prepares = current.prepares, stops = current.stops;
            current.position = 8000;
            state(current, Player.STATE_BUFFERING);
            assertFalse(h.owner.allowsSpeculativeWork());
            assertTrue(h.owner.summary().contains("realRebuffers=1"));
            h.prepare(1);
            assertEquals(0, neighbor.sourceAssignments);
            state(current, Player.STATE_READY);
            assertFalse(h.owner.allowsSpeculativeWork());
            for (int i = 0; i < 16; i++) {
                ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
                current.position += 1000;
                state(current, Player.STATE_READY);
            }
            assertTrue(h.owner.allowsSpeculativeWork());
            h.owner.permission(false, false);
            h.owner.permission(true, false); // Existing permission path flushes deferred sources.
            assertEquals(1, neighbor.sourceAssignments);
            assertEquals(assignments, current.sourceAssignments);
            assertEquals(prepares, current.prepares);
            assertEquals(stops, current.stops);
            assertTrue(current.position >= 24000);
            assertEquals(0f, neighbor.volume, 0f);
            assertFalse(neighbor.play);
            assertEquals(1, h.audible());
        }
    }
    @Test public void pauseAndPagingDoNotCountAsStarvationAndSelectionResetsProtection() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            h.select(0); h.prepare(0); h.attach(0);
            ShitTokPlayerPoolTest.Engine current = h.engine(0);
            state(current, Player.STATE_READY);
            h.owner.setUserPaused(ShitTokPlayerPoolTest.id(0), true);
            state(current, Player.STATE_BUFFERING);
            assertTrue(h.owner.summary().contains("realRebuffers=0"));
            h.owner.setUserPaused(ShitTokPlayerPoolTest.id(0), false);
            state(current, Player.STATE_READY);
            h.owner.permission(false, false);
            state(current, Player.STATE_BUFFERING);
            assertTrue(h.owner.summary().contains("realRebuffers=0"));
            h.owner.permission(true, false); state(current, Player.STATE_READY);
            state(current, Player.STATE_BUFFERING);
            assertFalse(h.owner.allowsSpeculativeWork());
            h.select(1); h.prepare(1); h.attach(1);
            assertTrue(h.owner.summary().contains("realRebuffers=0"));
            assertFalse(h.owner.summary().contains("warmingSuppressedByStarvation=true"));
            assertEquals(1, h.audible());
        }
    }
}
