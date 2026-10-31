package com.webapp.crazyshit;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShitTokRunwayPolicyTest {
    @Test public void normalRefillUsesHysteresisAndKeepsReserve() {
        ShitTokRunwayPolicy p = new ShitTokRunwayPolicy();
        assertTrue(p.needsWork(0, 0));
        assertTrue(p.needsWork(5, 0));
        assertFalse(p.needsWork(6, 0));
        assertFalse(p.needsWork(5, 0));
        assertTrue(p.needsWork(4, 0));
        assertTrue(p.needsWork(5, 0));
    }
    @Test public void sustainedForwardConsumptionRaisesTargetAndDecays() {
        ShitTokRunwayPolicy p = new ShitTokRunwayPolicy();
        p.forward(100); assertEquals(6, p.target(100));
        p.forward(300); assertEquals(10, p.target(300));
        assertEquals(6, p.threshold(300));
        assertEquals(6, p.target(4300));
        p.reset(); assertEquals(6, p.target(300));
    }
    @Test public void extremeHumanConsumptionRequestsRefillWhileSixItemsRemain() {
        ShitTokRunwayPolicy p = new ShitTokRunwayPolicy();
        p.forward(0); p.forward(100);
        assertFalse(p.needsWork(10, 100));
        for (int consumed = 1; consumed <= 100; consumed++) {
            long now = consumed * 100L;
            p.forward(now);
            assertEquals(10, p.target(now));
            assertFalse(p.needsWork(7, now));
            assertTrue(p.needsWork(6, now));
            assertTrue(p.needsWork(8, now));
            assertFalse(p.needsWork(10, now));
        }
    }
    @Test public void filteredTargetsStaySixAndTenAndResumeAtFiveAndEight() {
        ShitTokRunwayPolicy p = new ShitTokRunwayPolicy();
        assertEquals(6, p.target(0)); assertEquals(5, p.threshold(0, true));
        assertFalse(p.needsWork(6, 0, true)); assertTrue(p.needsWork(5, 0, true));
        p.forward(0); p.forward(100);
        assertEquals(10, p.target(100)); assertEquals(8, p.threshold(100, true));
        assertFalse(p.needsWork(10, 100, true)); assertFalse(p.needsWork(9, 100, true));
        assertTrue(p.needsWork(8, 100, true)); assertTrue(p.needsWork(9, 100, true));
        assertFalse(p.needsWork(10, 100, true));
        assertEquals(6, p.threshold(100)); assertEquals(4, p.threshold(5000));
    }
}
