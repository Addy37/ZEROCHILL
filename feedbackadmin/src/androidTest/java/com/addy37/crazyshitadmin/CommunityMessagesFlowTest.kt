package com.addy37.crazyshitadmin

import android.app.Application
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Focused fixture-only check for the read-only Community Messages moderation surface. */
class CommunityMessagesFlowTest {
    @get:Rule val ui = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val fake = MessageFixtureGateway()
    private lateinit var state: ConsoleState

    @After fun clean() {
        if (::state.isInitialized) ui.runOnUiThread { state.lock() }
    }

    @Test fun messagesSectionShowsBodyParticipantsAndReportedFilter() {
        ui.runOnUiThread {
            SecureTokenStore.clear(app)
            state = ConsoleState(app, SavedStateHandle(), fake)
        }
        ui.setContent { ConsoleTheme { CommunityScreen(state) } }

        ui.waitUntil(10_000) { state.communityOverview != null && state.busy.isEmpty() }
        ui.onNodeWithText("Messages").performClick()
        ui.waitUntil(10_000) { state.communityMessages.isNotEmpty() && state.busy.isEmpty() }

        ui.onNodeWithText("Fixture Sender → Fixture Recipient").assertExists()
        ui.onNodeWithText("Fixture moderation message body.").assertExists()
        ui.onNodeWithText("1 REPORT").assertExists()
        ui.onNodeWithText("ID 11111111").assertExists()

        ui.onNodeWithText("Reported").performClick()
        ui.waitUntil(10_000) { fake.lastMessageFilter == "reported" && state.busy.isEmpty() }
        assertEquals("reported", state.communityFilter)
        ui.onNodeWithText("Fixture moderation message body.").assertExists()
    }
}

private class MessageFixtureGateway : AdminGateway {
    @Volatile var lastMessageFilter = ""

    override fun communityOverview(token: String) = CommunityRepository.Overview(JSONObject("""{
        "accounts":2,"new_accounts_7d":1,"comments":0,"video_likes":0,
        "comment_likes":0,"messages":1,"reports":1,"blocks":0
    }"""))

    override fun accounts(token: String, query: String, filter: String) = emptyList<CommunityRepository.Account>()

    override fun directMessages(token: String, query: String, filter: String): List<CommunityRepository.DirectMessage> {
        lastMessageFilter = filter
        return listOf(CommunityRepository.DirectMessage(JSONObject("""{
            "id":"11111111-1111-4111-8111-111111111111",
            "sender_id":"22222222-2222-4222-8222-222222222222",
            "sender_name":"Fixture Sender",
            "recipient_id":"33333333-3333-4333-8333-333333333333",
            "recipient_name":"Fixture Recipient",
            "body":"Fixture moderation message body.",
            "created_at":"2026-10-27T21:33:22Z",
            "read_at":null,
            "report_count":1,
            "report_reasons":["spam"]
        }"""))
    }

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
