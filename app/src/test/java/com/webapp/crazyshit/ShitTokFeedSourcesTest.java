package com.webapp.crazyshit;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShitTokFeedSourcesTest {
    @Test public void wildSourcesBelongOnlyToChaos() {
        for (String source : new String[]{"crazyshit.com", "kaotic", "efukt", "shit-show"})
            assertTrue(source, ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.CHAOS, source));
        for (String source : new String[]{"onlyhaven", "fapello", "bunkr", "baddiehub", "xmegadrive.com"})
            assertFalse(source, ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.CHAOS, source));
    }

    @Test public void categorySourcesFollowApprovedLineups() {
        for (String source : new String[]{"onlyhaven", "fapello", "bunkr"}) {
            for (ShitTokCategoryStrip.Tab tab : new ShitTokCategoryStrip.Tab[]{
                    ShitTokCategoryStrip.Tab.LADIES, ShitTokCategoryStrip.Tab.GUYS,
                    ShitTokCategoryStrip.Tab.TRANS, ShitTokCategoryStrip.Tab.GROUPS}) {
                assertTrue(source + ": " + tab, ShitTokFeedSources.allows(tab, source));
            }
        }
        assertTrue(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.LADIES, "baddiehub"));
        assertTrue(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.GROUPS, "baddiehub"));
        assertTrue(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.LADIES, "shit-show"));
        for (ShitTokCategoryStrip.Tab tab : new ShitTokCategoryStrip.Tab[]{
                ShitTokCategoryStrip.Tab.GUYS, ShitTokCategoryStrip.Tab.TRANS,
                ShitTokCategoryStrip.Tab.GROUPS}) {
            assertFalse(ShitTokFeedSources.allows(tab, "shit-show"));
        }
        assertFalse(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.GUYS, "baddiehub"));
        assertFalse(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.TRANS, "baddiehub"));
    }

    @Test public void landscapeEligibilityIsExtraOnlyWhenCallerAsksForIt() {
        assertFalse(ShitTokOrientation.eligible(16f / 9f));
        assertTrue(ShitTokOrientation.immersiveEligible(16f / 9f));
        assertTrue(ShitTokOrientation.immersiveEligible(9f / 16f));
        assertFalse(ShitTokOrientation.immersiveEligible(3f));
        assertFalse(ShitTokOrientation.immersiveEligible(Float.NaN));
    }
}
