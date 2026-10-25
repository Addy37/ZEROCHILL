package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XMegaDriveAgeGateSafetyTest {
    @Test public void directCookieUsesOnlyDiscoveredAgeMarker() {
        String directive = XMegaDriveAgeSession.cookieDirective();
        assertTrue(directive.startsWith("kt_agecheck=1;"));
        assertTrue(directive.contains("Max-Age=31536000"));
        assertTrue(directive.contains("Path=/"));
        assertTrue(directive.contains("SameSite=Lax"));
        assertTrue(directive.contains("Secure"));
        assertFalse(directive.contains("cf_clearance"));
        assertFalse(directive.contains("__cf_bm"));
    }

    @Test public void markerRequiresExactNameAndValue() {
        assertTrue(XMegaDriveAgeSession.hasAgeCookieHeader("foo=a; kt_agecheck=1; bar=b"));
        assertFalse(XMegaDriveAgeSession.hasAgeCookieHeader("kt_agecheck=0"));
        assertFalse(XMegaDriveAgeSession.hasAgeCookieHeader("other_kt_agecheck=1"));
        assertFalse(XMegaDriveAgeSession.hasAgeCookieHeader(null));
    }
}
