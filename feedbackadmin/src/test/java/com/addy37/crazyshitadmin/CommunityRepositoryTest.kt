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
    @Test fun dmPageParsesFullMessageAndAttachmentWithoutChangingMetadataView() {
        val page = CommunityRepository.DmPage(JSONObject("""
        {
            "items": [
              {"id":"a1","sender_id":"u1","sender_name":"Sender",
               "recipient_id":"u2","recipient_name":"Recipient",
               "body":"Full private message, not a preview","created_at":"2026-11-19T14:00:00Z",
               "read_at":null,"has_attachment":true,"attachment_kind":"Photo",
               "media_url":"https://example.supabase.co/storage/v1/object/sign/test"}
            ],
            "next_cursor":"2026-11-19T14:00:00Z|a1"
        }
        """.trimIndent()))
        assertEquals(1, page.items.size)
        val message = page.items[0]
        assertEquals("Full private message, not a preview", message.body)
        assertEquals("Sender", message.senderName)
        assertEquals("Recipient", message.recipientName)
        assertEquals("", message.readAt)
        assertTrue(message.hasAttachment)
        assertEquals("Photo", message.attachmentKind)
        assertTrue(message.mediaUrl.startsWith("https://"))
        assertEquals("2026-11-19T14:00:00Z|a1", page.nextCursor)
        assertEquals("", CommunityRepository.DmPage(JSONObject("{\"items\":[]}")).nextCursor)
    }

    @Test fun threadedConversationSummaryParsesParticipantsCountsAndReports() {
        val conversation = CommunityRepository.DmConversation(JSONObject("""
        {
            "participant_a_id":"11111111-1111-4111-8111-111111111111",
            "participant_a_name":"Addy",
            "participant_b_id":"22222222-2222-4222-8222-222222222222",
            "participant_b_name":"@vjplus",
            "last_message_id":"33333333-3333-4333-8333-333333333333",
            "last_message_body":"Recent message",
            "last_sender_name":"Addy",
            "last_created_at":"2026-11-19T15:30:00Z",
            "message_count":18,
            "unread_count":2,
            "report_count":1,
            "report_reasons":["spam"]
        }
        """.trimIndent()))
        assertEquals("11111111-1111-4111-8111-111111111111:22222222-2222-4222-8222-222222222222", conversation.key())
        assertEquals("@vjplus", conversation.participantBName)
        assertEquals("Recent message", conversation.lastMessageBody)
        assertEquals(18L, conversation.messageCount)
        assertEquals(2L, conversation.unreadCount)
        assertEquals(1L, conversation.reportCount)
        assertEquals(listOf("spam"), conversation.reportReasons)
    }

    @Test fun scopedMessagePageParsesReportMarkersAndOlderCursor() {
        val page = CommunityRepository.DmPage(JSONObject("""
        {
            "items":[
                {"id":"dm1","sender_id":"userA","sender_name":"User A",
                 "recipient_id":"userB","recipient_name":"User B","body":"An older message",
                 "created_at":"2026-11-18T10:00:00Z",
                 "report_count":2,"report_reasons":["harassment","spam"],
                 "read_at":null,"has_attachment":false}
            ],
            "next_cursor":"2026-11-18T10:00:00Z|dm1"
        }
        """.trimIndent()))
        assertEquals(1, page.items.size)
        assertEquals("An older message", page.items[0].body)
        assertEquals(2L, page.items[0].reportCount)
        assertEquals(listOf("harassment", "spam"), page.items[0].reportReasons)
        assertEquals("2026-11-18T10:00:00Z|dm1", page.nextCursor)
    }

}
