package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XMegaDriveAgeGateSafetyTest {
    @Test public void visibleFallbackHidesAndBlocksDeclineControls() {
        String script = XMegaDriveBrowserFetch.ageGateSafetyScript();
        assertTrue(script.contains("ageDecline"));
        assertTrue(script.contains("style.display='none'"));
        assertTrue(script.contains("aria-hidden"));
        assertTrue(script.contains("stopImmediatePropagation"));
        assertTrue(script.contains("ageAccept"));
        assertTrue(script.contains("el.click()"));
        assertTrue(script.contains("\\b(exit|leave|decline)\\b"));
        assertFalse(script.contains("ageDecline(ageLabel(el)))continue;el.click()"));
    }
}
