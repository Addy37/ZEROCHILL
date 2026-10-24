package com.webapp.crazyshit;

import android.app.Application;
import android.webkit.CookieManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Age confirmation stays off-screen. Continue is chosen without Exit.
 * A declined or empty result can clear only XMegaDrive cookies, once.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class XMegaDriveAgeGateTest {
    @Test public void ageConfirmationIsNotRevealed() {
        assertFalse(XMegaDriveBrowserFetch.shouldReveal(
                "Age confirmation", "You must be 18. Continue Exit"));
        assertFalse(XMegaDriveBrowserFetch.shouldReveal("xMegaDrive", "Age confirmation"));
        assertFalse(XMegaDriveBrowserFetch.isAgeConfirmation("Just a moment", "Checking your browser"));
    }

    @Test public void humanChallengesAreStillRevealed() {
        assertTrue(XMegaDriveBrowserFetch.shouldReveal("Just a moment", "Verify you are human"));
        assertTrue(XMegaDriveBrowserFetch.shouldReveal("", "Complete the Turnstile challenge"));
        assertFalse(XMegaDriveBrowserFetch.shouldReveal("Just a moment", "Checking your browser"));
    }

    @Test public void autoContinueMatchesAcceptControlsAndNeverExit() {
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("Continue"));
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("  CONTINUE  "));
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("I am 18"));
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("I'm 18"));
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("I’m 18"));
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("Im 18"));
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("I am 18 or older"));
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("Yes"));
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("Enter"));
        assertTrue(XMegaDriveBrowserFetch.acceptsAgeControl("Enter site"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("Exit"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("Leave"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("Decline"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("No"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("No thanks"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("Not 18"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("I am not 18"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("Under 18"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("Center"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl("Yesterday"));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl(""));
        assertFalse(XMegaDriveBrowserFetch.acceptsAgeControl(null));
    }

    @Test public void ageScriptClicksBroaderAcceptControlsAndSkipsDecline() {
        String script = XMegaDriveBrowserFetch.ageAcceptScript();
        assertTrue(script.contains("input[type=\"button\"]"));
        assertTrue(script.contains("input[type=\"submit\"]"));
        assertTrue(script.contains("[role=\"button\"]"));
        assertTrue(script.contains("ageDecline"));
        assertTrue(script.contains("!ageDecline(s)"));
        assertTrue(script.contains("\\bcontinue\\b"));
        assertTrue(script.contains("el.click()"));
        assertTrue(script.contains("age confirmation"));
        assertFalse(script.contains("Tap Continue"));
    }

    @Test public void declinedOrEmptyPageRecoversOnceAndDoesNotLoop() {
        assertEquals(8, XMegaDriveBrowserFetch.AGE_ACCEPT_ATTEMPTS);
        assertFalse(XMegaDriveBrowserFetch.shouldRecoverNonContent(
                true, false, false, true, 9, false));
        assertFalse(XMegaDriveBrowserFetch.shouldRecoverNonContent(
                false, true, false, true, 9, false));
        assertFalse(XMegaDriveBrowserFetch.shouldRecoverNonContent(
                false, false, true, true, 9, false));
        assertFalse(XMegaDriveBrowserFetch.shouldRecoverNonContent(
                false, false, false, true, 9, true));
        assertFalse(XMegaDriveBrowserFetch.shouldRecoverNonContent(
                false, false, false, false, 7, false));
        assertTrue(XMegaDriveBrowserFetch.shouldRecoverNonContent(
                false, false, false, false, 8, false));
        assertTrue(XMegaDriveBrowserFetch.shouldRecoverNonContent(
                false, false, false, true, 1, false));
        assertTrue(XMegaDriveBrowserFetch.looksDeclined(
                "", "You are under 18", "https://www.xmegadrive.com/"));
        assertTrue(XMegaDriveBrowserFetch.looksDeclined(
                "", "", "https://www.google.com/"));
        assertFalse(XMegaDriveBrowserFetch.looksDeclined(
                "", "videos", "https://www.xmegadrive.com/videos/"));
        assertFalse(XMegaDriveBrowserFetch.isOffsite("https://challenges.cloudflare.com/cdn-cgi/challenge"));
        assertFalse(XMegaDriveBrowserFetch.isOffsite("about:blank"));
    }

    @Test public void cookieRecoveryTargetsOnlyXMegaDrive() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setCookie("https://www.xmegadrive.com/", "age_declined=1");
        cookies.setCookie("https://www.xmegadrive.com/", "cf_clearance=session-value");
        cookies.setCookie("https://other.example/", "provider=kept");
        String site = cookies.getCookie("https://www.xmegadrive.com/");
        List<String> directives = XMegaDriveBrowserFetch.expireDirectives(site);
        String joined = String.join("\n", directives);
        assertTrue(joined.contains("age_declined="));
        assertTrue(joined.contains("cf_clearance="));
        assertTrue(joined.contains("Domain=.xmegadrive.com"));
        assertFalse(joined.contains("session-value"));
        assertFalse(joined.contains("provider"));
        assertTrue(XMegaDriveBrowserFetch.expireDirectives(null).isEmpty());
        assertTrue(XMegaDriveBrowserFetch.expireDirectives("").isEmpty());

        List<String> urls = XMegaDriveBrowserFetch.siteCookieUrls("https://cdn.xmegadrive.com/file");
        assertTrue(urls.contains("https://www.xmegadrive.com/"));
        assertTrue(urls.contains("https://xmegadrive.com/"));
        assertTrue(urls.contains("https://cdn.xmegadrive.com/"));
        assertFalse(urls.contains("https://other.example/"));
        assertFalse(XMegaDriveBrowserFetch.siteCookieUrls("https://other.example/home")
                .contains("https://other.example/"));

        XMegaDriveBrowserFetch.clearSiteCookies("https://www.xmegadrive.com/videos/");
        String other = cookies.getCookie("https://other.example/");
        assertTrue(other != null && other.contains("provider=kept"));
    }
}
