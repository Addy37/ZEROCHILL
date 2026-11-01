package com.webapp.crazyshit;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalyticsV2PolicyTest {
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
