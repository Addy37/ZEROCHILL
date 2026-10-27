package com.addy37.crazyshitadmin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveCountsTest {
    @Test fun parsesLightweightPayload() {
        val counts = AdminRepository.ActiveCounts(JSONObject("""
            {
              "generated_at":"2026-10-20T12:45:00Z",
              "active_users":{"daily":298,"weekly":594,"monthly":2398}
            }
        """))

        assertEquals(298L, counts.dailyUsers)
        assertEquals(594L, counts.weeklyUsers)
        assertEquals(2398L, counts.monthlyUsers)
        assertEquals("2026-10-20T12:45:00Z", counts.generatedAt)
    }

    @Test fun fullDashboardSeedsLiveCountsWithoutNeedingRankings() {
        val dashboard = AdminRepository.AnalyticsDashboard(JSONObject("""
            {
              "generated_at":"2026-10-20T12:40:00Z",
              "active_users":{"daily":12,"weekly":34,"monthly":56},
              "creators":[{"value":"Creator A","unique_users":7,"event_count":11}]
            }
        """))
        val counts = AdminRepository.ActiveCounts(dashboard)

        assertEquals(12L, counts.dailyUsers)
        assertEquals(34L, counts.weeklyUsers)
        assertEquals(56L, counts.monthlyUsers)
        assertEquals("2026-10-20T12:40:00Z", counts.generatedAt)
        assertEquals("Creator A", dashboard.creators.first().value)
    }
}
