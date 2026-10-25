package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XMegaDriveAgeGateSafetyTest {
    @Test public void visibleFallbackHidesAndBlocksDeclineControlsWithoutSyntheticAccept() {
        String script = XMegaDriveBrowserFetch.ageGateSafetyScript();
        assertTrue(script.contains("ageDecline"));
        assertTrue(script.contains("style.display='none'"));
        assertTrue(script.contains("aria-hidden"));
        assertTrue(script.contains("tabindex"));
        assertTrue(script.contains("disabled"));
        assertTrue(script.contains("document.addEventListener('click'"));
        assertTrue(script.contains("stopImmediatePropagation"));
        assertFalse(script.contains("el.click()"));
        assertFalse(script.contains("ageAccept"));
    }

    @Test public void nativePromptLocatesAndSizesRealAcceptControlWithoutSyntheticClick() {
        String script = XMegaDriveBrowserFetch.ageGateNativeTapTargetScript();
        assertTrue(script.contains("style.display='none'"));
        assertTrue(script.contains("aria-hidden"));
        assertTrue(script.contains("disabled"));
        assertTrue(script.contains("scrollIntoView"));
        assertTrue(script.contains("getBoundingClientRect"));
        assertTrue(script.contains("w:r.width"));
        assertTrue(script.contains("h:r.height"));
        assertTrue(script.contains("window.innerWidth"));
        assertTrue(script.contains("window.innerHeight"));
        assertTrue(script.contains("JSON.stringify"));
        assertTrue(script.contains("shadowRoot"));
        assertTrue(script.contains("contentDocument"));
        assertTrue(script.contains("depth>5"));
        assertFalse(script.contains("el.click()"));
        assertFalse(script.contains("document.activeElement"));
    }
}
