package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreloadRequestTrackerTest {
    @Test public void duplicatePreloadsAreSuppressed() {
        PreloadRequestTracker tracker = new PreloadRequestTracker(3);
        assertTrue(tracker.markIfNew("one"));
        assertFalse(tracker.markIfNew("one"));
        assertEquals(1, tracker.size());
    }

    @Test public void trackerStaysBoundedAndOldKeysCanBeScheduledAgain() {
        PreloadRequestTracker tracker = new PreloadRequestTracker(2);
        assertTrue(tracker.markIfNew("one"));
        assertTrue(tracker.markIfNew("two"));
        assertTrue(tracker.markIfNew("three"));
        assertEquals(2, tracker.size());
        assertTrue(tracker.markIfNew("one"));
        assertEquals(2, tracker.size());
    }
}
