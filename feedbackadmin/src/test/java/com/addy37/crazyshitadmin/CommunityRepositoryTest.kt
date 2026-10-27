package com.addy37.crazyshitadmin

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommunityRepositoryTest {
    @Test fun overviewAndAccountPayloadsParse() {
        val overview = CommunityRepository.Overview(JSONObject("""{
            "accounts":443,"new_accounts_7d":169,"comments":96,"video_likes":2416,
            "comment_likes":9,"messages":70,"reports":3,"blocks":4
        }"""))
        assertEquals(443, overview.accounts)
        assertEquals(2425, overview.totalLikes())
        assertEquals(3, overview.reports)

        val account = CommunityRepository.Account(JSONObject("""{
            "id":"b20aa450-3550-4a10-9a77-000000000001",
            "email":"user@example.com","username":"addy","display_name":"Addy",
            "bio":"Hello","avatar_path":"avatars/a.jpg","status":"active",
            "created_at":"2026-10-20T00:00:00Z","last_sign_in_at":"2026-10-21T00:00:00Z"
        }"""))
        assertEquals("Addy", account.name())
        assertEquals("@addy", account.handle())
        assertEquals("active", account.status)
        assertEquals("", account.bannedUntil)
    }

    @Test fun detailKeepsMessageMetadataWithoutBodyField() {
        val detail = CommunityRepository.AccountDetail(JSONObject("""{
            "account":{"id":"b20aa450-3550-4a10-9a77-000000000001","display_name":"Addy","status":"active"},
            "metrics":{"comments":4,"video_likes":7,"comment_likes":2,"messages_sent":3,"messages_received":5,"reports_filed":1,"reports_received":2,"blocks_made":1,"blocks_received":0},
            "recent_messages":[{"type":"message","id":"dm1","user_id":"b20aa450-3550-4a10-9a77-000000000001","display_name":"Addy","text":"","secondary":"To Other User","created_at":"2026-10-20T04:32:00Z","state":"read"}],
            "recent_reports":[{"id":"r1","reporter_id":"u1","reporter_name":"Reporter","reported_user_id":"u2","reported_name":"Reported","reason":"spam","details":"Details","created_at":"2026-10-20T04:33:00Z"}]
        }"""))
        assertEquals(4, detail.comments)
        assertEquals(3, detail.messagesSent)
        assertEquals(5, detail.messagesReceived)
        assertEquals(1, detail.recentMessages.size)
        assertEquals("", detail.recentMessages[0].text)
        assertEquals("To Other User", detail.recentMessages[0].secondary)
        assertEquals("spam", detail.recentReports[0].reason)
    }
}
