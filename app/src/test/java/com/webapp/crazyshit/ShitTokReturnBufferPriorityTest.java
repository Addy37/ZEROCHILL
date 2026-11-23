package com.webapp.crazyshit;

import org.junit.Test;
import static org.junit.Assert.*;

/** Existing saved playlists should buffer before optional discovery, but never starve fresh feeds. */
public class ShitTokReturnBufferPriorityTest {
    @Test public void cachedReturnWithTwoUpcomingClipsGetsBriefPlaybackPriority() {
        assertTrue(ShitTokReturnBufferPriority.shouldDeferDiscovery(true, 2));
        assertTrue(ShitTokReturnBufferPriority.shouldDeferDiscovery(true, 20));
        assertEquals(650L, ShitTokReturnBufferPriority.DISCOVERY_GRACE_MS);
    }

    @Test public void freshOrSparseFeedNeverWaitsForDiscovery() {
        assertFalse(ShitTokReturnBufferPriority.shouldDeferDiscovery(false, 10));
        assertFalse(ShitTokReturnBufferPriority.shouldDeferDiscovery(true, 0));
        assertFalse(ShitTokReturnBufferPriority.shouldDeferDiscovery(true, 1));
    }
}
