package com.addy37.crazyshitadmin

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Focused fixture-only check for grouped read-only Community Messages moderation. */
class CommunityMessagesFlowTest {
    @get:Rule val ui = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val fake = MessageFixtureGateway()
    private lateinit var state: ConsoleState

    @After fun clean() {
        if (::state.isInitialized) ui.runOnUiThread { state.lock() }
    }

    @Test fun messagesSectionGroupsConversationAndOpensThread() {
        ui.runOnUiThread {
            SecureTokenStore.clear(app)
            state = ConsoleState(app, SavedStateHandle(), fake)
        }
        ui.setContent { ConsoleTheme { CommunityScreen(state) } }

        ui.waitUntil(10_000) { state.communityOverview != null && state.busy.isEmpty() }
        ui.onNodeWithText("Messages").performClick()
        ui.waitUntil(10_000) { state.communityConversations.isNotEmpty() && state.busy.isEmpty() }

        ui.onNodeWithText("Fixture Sender ↔ Fixture Recipient").assertExists()
        ui.onNodeWithText("Fixture Sender: Latest moderation message.").assertExists()
        ui.onNodeWithText("2 messages · 1 unread").assertExists()
        ui.onNodeWithText("1 REPORT").assertExists()

        ui.onNodeWithText("Fixture Sender ↔ Fixture Recipient").performClick()
        ui.waitUntil(10_000) { state.communityMessages.size == 2 && state.busy.isEmpty() }
        ui.onNodeWithText("Earlier moderation message.").assertExists()
        ui.onNodeWithText("Latest moderation message.").assertExists()
        ui.onNodeWithText("ID 11111111").assertExists()
        ui.onNodeWithText("Close").performClick()

        ui.onNodeWithText("Reported").performClick()
        ui.waitUntil(10_000) { fake.lastConversationFilter == "reported" && state.busy.isEmpty() }
        assertEquals("reported", state.communityFilter)
        ui.onNodeWithText("Fixture Sender: Latest moderation message.").assertExists()
    }
}

private class MessageFixtureGateway : AdminGateway {
    @Volatile var lastConversationFilter = ""

    override fun communityOverview(token: String) = CommunityRepository.Overview(JSONObject("""{
        "accounts":2,"new_accounts_7d":1,"comments":0,"video_likes":0,
        "comment_likes":0,"messages":2,"reports":1,"blocks":0
    }"""))

    override fun accounts(token: String, query: String, filter: String) = emptyList<CommunityRepository.Account>()

    override fun conversations(token: String, query: String, filter: String): List<CommunityRepository.Conversation> {
        lastConversationFilter = filter
        return listOf(CommunityRepository.Conversation(JSONObject("""{
            "participant_a_id":"22222222-2222-4222-8222-222222222222",
            "participant_a_name":"Fixture Sender",
            "participant_b_id":"33333333-3333-4333-8333-333333333333",
            "participant_b_name":"Fixture Recipient",
            "last_message_id":"11111111-1111-4111-8111-111111111111",
            "last_message_body":"Latest moderation message.",
            "last_sender_id":"22222222-2222-4222-8222-222222222222",
            "last_sender_name":"Fixture Sender",
            "last_created_at":"2026-10-27T21:33:22Z",
            "message_count":2,
            "unread_count":1,
            "report_count":1,
            "report_reasons":["spam"]
        }""")))
    }

    override fun conversationMessages(token: String, participantAId: String, participantBId: String): List<CommunityRepository.DirectMessage> = listOf(
        CommunityRepository.DirectMessage(JSONObject("""{
            "id":"00000000-0000-4000-8000-000000000001",
            "sender_id":"33333333-3333-4333-8333-333333333333",
            "sender_name":"Fixture Recipient",
            "recipient_id":"22222222-2222-4222-8222-222222222222",
            "recipient_name":"Fixture Sender",
            "body":"Earlier moderation message.",
            "created_at":"2026-10-27T21:30:00Z",
            "read_at":"2026-10-27T21:31:00Z",
            "report_count":0,
            "report_reasons":[]
        }""")),
        CommunityRepository.DirectMessage(JSONObject("""{
            "id":"11111111-1111-4111-8111-111111111111",
            "sender_id":"22222222-2222-4222-8222-222222222222",
            "sender_name":"Fixture Sender",
            "recipient_id":"33333333-3333-4333-8333-333333333333",
            "recipient_name":"Fixture Recipient",
            "body":"Latest moderation message.",
            "created_at":"2026-10-27T21:33:22Z",
            "read_at":null,
            "report_count":1,
            "report_reasons":["spam"]
        }"""))
    )

    override fun list(token: String): List<AdminRepository.Item> = error("unused")
    override fun analytics(token: String): AdminRepository.AnalyticsDashboard = error("unused")
    override fun currentConfig(token: String): JSONObject? = error("unused")
    override fun configHistory(token: String): List<AdminRepository.ConfigVersion> = error("unused")
    override fun validateConfig(token: String, config: JSONObject): Long = error("unused")
    override fun publishConfig(token: String, config: JSONObject): Long = error("unused")
    override fun rollbackConfig(token: String, version: Long): Long = error("unused")
    override fun thread(token: String, id: String): AdminRepository.FeedbackThread = error("unused")
    override fun reply(token: String, id: String, status: String, message: String) = error("unused")
    override fun updateStatus(token: String, id: String, status: String) = error("unused")
    override fun deleteFeedback(token: String, id: String) = error("unused")
    override fun accountDetail(token: String, userId: String): CommunityRepository.AccountDetail = error("unused")
    override fun socialActivity(token: String, query: String, filter: String): List<CommunityRepository.SocialActivity> = error("unused")
    override fun reports(token: String, query: String, filter: String): List<CommunityRepository.Report> = error("unused")
}
