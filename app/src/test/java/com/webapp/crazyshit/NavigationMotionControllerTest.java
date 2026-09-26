package com.webapp.crazyshit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavigationMotionControllerTest {
    private static final int SHOWS = MainPagerAdapter.PAGE_SERIES;
    private static final int ONLYFAP = MainPagerAdapter.PAGE_ONLYFAP;
    private final List<Boolean> requests = new ArrayList<>();
    private final NavigationMotionController motion =
            new NavigationMotionController(requests::add, 10, SHOWS);

    @Test public void tabsPreserveCompactAndExpandedTargets() {
        motion.beginGesture(SHOWS);
        motion.onScroll(SHOWS, 12, false, true);
        assertTrue(motion.isCollapsed());
        motion.setActivePage(ONLYFAP);
        motion.onScroll(SHOWS, -30, true, true);
        motion.onScroll(ONLYFAP, 0, true, true);
        assertTrue(motion.isCollapsed());
        assertEquals(Arrays.asList(true), requests);

        motion.beginGesture(ONLYFAP);
        motion.onScroll(ONLYFAP, -12, false, true);
        assertFalse(motion.isCollapsed());
        motion.setActivePage(SHOWS);
        motion.onScroll(ONLYFAP, 30, false, true);
        assertFalse(motion.isCollapsed());
        assertEquals(Arrays.asList(true, false), requests);
    }

    @Test public void activeUserMovementAndGenuineTopControlTarget() {
        motion.beginGesture(SHOWS);
        motion.onScroll(SHOWS, 5, false, true);
        motion.onScroll(SHOWS, 6, false, true);
        assertTrue(motion.isCollapsed());
        motion.onScroll(SHOWS, -4, false, true);
        motion.onScroll(SHOWS, -6, false, true);
        assertFalse(motion.isCollapsed());
        motion.onScroll(SHOWS, 12, false, true);
        motion.onScroll(SHOWS, -1, true, true);
        assertFalse(motion.isCollapsed());
        assertEquals(Arrays.asList(true, false, true, false), requests);
    }

    @Test public void restorationAndRepeatedRequestsCannotRestartMotion() {
        motion.onScroll(SHOWS, 80, false, true); // no touch gesture
        motion.onScroll(SHOWS, 0, true, true); // layout
        motion.beginGesture(SHOWS);
        motion.onScroll(SHOWS, 80, false, false); // programmatic movement
        assertTrue(requests.isEmpty());
        motion.onScroll(SHOWS, 12, false, true);
        motion.onScroll(SHOWS, 12, false, true);
        motion.onScroll(SHOWS, 12, false, true);
        assertEquals(Arrays.asList(true), requests);
        motion.onScroll(SHOWS, -12, false, true);
        motion.onScroll(SHOWS, -12, false, true);
        assertEquals(Arrays.asList(true, false), requests);
        motion.endGesture();
        motion.onScroll(SHOWS, 30, false, true);
        assertEquals(Arrays.asList(true, false), requests);
    }

    @Test public void shitTokIgnoresProgrammaticPagingAndOtherPages() {
        motion.setActivePage(MainPagerAdapter.PAGE_CHAOS);
        motion.onScroll(MainPagerAdapter.PAGE_CHAOS, 24, false, false);
        motion.onScroll(SHOWS, 24, false, true);
        assertFalse(motion.isCollapsed());
        motion.onScroll(MainPagerAdapter.PAGE_CHAOS, 24, false, true);
        motion.onScroll(MainPagerAdapter.PAGE_CHAOS, -24, true, true);
        assertEquals(Arrays.asList(true, false), requests);
    }
}
