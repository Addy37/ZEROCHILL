package com.webapp.crazyshit;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalyticsV2PolicyTest {
 @Test public void adoptionCountsLegacyUpgradesButNotFreshInstallsOrRepeatedStarts() {
  assertFalse(AnalyticsV2Policy.adoptedUpdate("","5.7.0",100,100));
  assertTrue(AnalyticsV2Policy.adoptedUpdate("","5.7.0",100,200));
  assertFalse(AnalyticsV2Policy.adoptedUpdate("5.7.0","5.7.0",100,200));
  assertTrue(AnalyticsV2Policy.adoptedUpdate("5.7.0","5.7.1",100,300));
 }

 @Test public void approvedCountersAndErrorsHaveFiniteCategories() {
  for(String m:new String[]{"sessions","session_ms","shittok_videos","search","favorites","comments","likes","dms","account_signin"}) assertTrue(AnalyticsV2Policy.allowed(m,"all"));
  for(String m:new String[]{"source","creator","downloads","focus_mode","history","recipient_id","attachment_url"}) assertFalse(AnalyticsV2Policy.allowed(m,"all"));
  assertFalse(AnalyticsV2Policy.allowed("backend_error","secret exception or URL"));
  assertFalse(AnalyticsV2Policy.allowed("section","downloads"));
  assertEquals("rate_limit",AnalyticsV2Policy.backendCategory(429));
  assertEquals("permission",AnalyticsV2Policy.backendCategory(403));
  assertEquals("network",AnalyticsV2Policy.backendCategory(0));
 }
}
