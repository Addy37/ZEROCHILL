package com.addy37.crazyshitadmin
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class AnalyticsV2Test {
 @Test fun parsesOnlyAggregateV2MetricsAndAverages() {
  val a=AdminRepository.AnalyticsDashboard(JSONObject("""{"active_users":{"daily":1,"weekly":2,"monthly":3},"metrics":[{"value":"backend_error / network","event_count":4}],"average_session_seconds":30.5,"average_shittok_videos":8.25}"""))
  assertEquals(1L,a.dailyUsers);assertEquals(4L,a.metrics[0].eventCount)
  assertEquals(30.5,a.averageSessionSeconds,0.001);assertEquals(8.25,a.averageShittokVideos,0.001)
  assertTrue(a.sources.isEmpty());assertTrue(a.creators.isEmpty())
 }
 @Test fun rendersEasternTimeAcrossDst() {
  assertTrue(easternLabel("2026-03-08T06:30:00Z").endsWith("EST"))
  assertTrue(easternLabel("2026-03-08T07:30:00Z").endsWith("EDT"))
  assertTrue(easternLabel("2026-11-01T05:30:00Z").endsWith("EDT"))
  assertTrue(easternLabel("2026-11-01T06:30:00Z").endsWith("EST"))
 }
}
