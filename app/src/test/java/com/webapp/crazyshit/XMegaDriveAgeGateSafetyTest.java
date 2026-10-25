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
        assertTrue(script.contains("tabindex"));
        assertTrue(script.contains("disabled"));
        assertTrue(script.contains("document.addEventListener('click'"));
        assertTrue(script.contains("stopImmediatePropagation"));
        assertTrue(script.contains("ageAccept"));
        assertTrue(script.contains("el.click()"));
        assertTrue(script.contains("\\b(exit|leave|decline)\\b"));
        assertTrue(script.indexOf("hideDecline") < script.lastIndexOf("el.click()"));
        assertFalse(script.contains("ageDecline(ageLabel(el)))continue;el.click()"));
    }

    @Test public void nativePromptLocatesAcceptControlForAndroidTouchWithoutSyntheticClick() {
        String script = XMegaDriveBrowserFetch.ageGateNativeTapTargetScript();
        assertTrue(script.contains("style.display='none'"));
        assertTrue(script.contains("aria-hidden"));
        assertTrue(script.contains("disabled"));
        assertTrue(script.contains("scrollIntoView"));
        assertTrue(script.contains("getBoundingClientRect"));
        assertTrue(script.contains("window.innerWidth"));
        assertTrue(script.contains("window.innerHeight"));
        assertTrue(script.contains("JSON.stringify"));
        assertTrue(script.contains("\b(exit|leave|decline)\b"));
        assertTrue(script.contains("i(?:'m| am|m) 18"));
        assertFalse(script.contains("el.click()"));
        assertFalse(script.contains("document.activeElement"));
    }
}
