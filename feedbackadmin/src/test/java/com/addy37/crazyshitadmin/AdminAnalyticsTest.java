package com.addy37.crazyshitadmin;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminAnalyticsTest {
    @Test
    public void parsesSocialAndReleaseAnalytics() throws Exception {
        JSONObject root = new JSONObject()
                .put("active_users", new JSONObject()
                        .put("daily", 20).put("weekly", 50).put("monthly", 100))
                .put("favorite_creators", new org.json.JSONArray()
                        .put(new JSONObject().put("value", "Demon Mika").put("favorite_count", 3))
                        .put(new JSONObject().put("value", "Sasha Grey").put("favorite_count", 2)))
                .put("release_adoption", new JSONObject()
                        .put("version", "4.3.0")
                        .put("users_today", 18)
                        .put("users_week", 35)
                        .put("users_month", 40)
                        .put("percent_today", 90.0)
                        .put("percent_week", 70.0)
                        .put("percent_month", 40.0))
                .put("social", new JSONObject()
                        .put("accounts", new JSONObject()
                                .put("total", 27).put("new_today", 4).put("new_week", 9))
                        .put("profile_adoption", new JSONObject()
                                .put("with_avatar", 12).put("with_bio", 8))
                        .put("social_users", new JSONObject()
                                .put("total", 18).put("today", 7).put("week", 14))
                        .put("comments", new JSONObject()
                                .put("total", 54).put("today", 6).put("week", 31)
                                .put("replies_total", 20).put("replies_week", 12))
                        .put("likes", new JSONObject()
                                .put("video_total", 44).put("video_week", 21)
                                .put("comment_total", 16).put("comment_week", 9))
                        .put("messages", new JSONObject()
                                .put("total", 146).put("today", 18).put("week", 83)
                                .put("senders_week", 11).put("conversations_week", 7)
                                .put("read", 120).put("unread", 26)
                                .put("read_rate_percent", 82.2))
                        .put("creator_favorites", new JSONObject()
                                .put("total", 61).put("accounts", 15)
                                .put("average_per_account", 4.1))
                        .put("safety", new JSONObject().put("blocks", 2))
                        .put("notification_preferences", new JSONObject()
                                .put("customized_users", 5)
                                .put("replies_disabled", 1)
                                .put("likes_disabled", 2)
                                .put("direct_messages_disabled", 1)));

        AdminRepository.AnalyticsDashboard dashboard =
                new AdminRepository.AnalyticsDashboard(root);

        assertEquals(27, dashboard.social.accountsTotal);
        assertEquals(18, dashboard.social.socialUsersTotal);
        assertEquals(146, dashboard.social.messagesTotal);
        assertEquals(54, dashboard.social.commentsTotal);
        assertEquals(7, dashboard.social.conversationsWeek);
        assertEquals(82.2, dashboard.social.messageReadRate, 0.01);
        assertEquals(15, dashboard.social.creatorFavoriteAccounts);
        assertEquals(2, dashboard.favoriteCreators.size());
        assertEquals("Demon Mika", dashboard.favoriteCreators.get(0).value);
        assertEquals(3, dashboard.favoriteCreators.get(0).favoriteCount);
        assertEquals("4.3.0", dashboard.releaseAdoption.version);
        assertEquals(70.0, dashboard.releaseAdoption.percentWeek, 0.01);
    }

    @Test
    public void compactListsShowFiveByDefault() {
        assertEquals(5, AnalyticsListPolicy.COLLAPSED_ROWS);
        assertTrue(AnalyticsListPolicy.shouldShow(0, false));
        assertTrue(AnalyticsListPolicy.shouldShow(4, false));
        assertFalse(AnalyticsListPolicy.shouldShow(5, false));
        assertTrue(AnalyticsListPolicy.shouldShow(5, true));
        assertFalse(AnalyticsListPolicy.needsToggle(5));
        assertTrue(AnalyticsListPolicy.needsToggle(6));
        assertEquals("View all 12 ↓", AnalyticsListPolicy.toggleLabel(12, false));
        assertEquals("Show less ↑", AnalyticsListPolicy.toggleLabel(12, true));
    }
}
