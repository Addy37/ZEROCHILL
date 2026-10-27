package com.addy37.crazyshitadmin

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Fixture-only UI verification. Never contacts the hosted backend or publishes real configuration. */
class ConsoleFlowTest {
    @get:Rule val ui = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val fake = FixtureGateway()
    private lateinit var state: ConsoleState
    private val saved = SavedStateHandle()
    private fun launch(pair: Boolean=true) {
        ui.runOnUiThread {SecureTokenStore.clear(app);state=ConsoleState(app,saved,fake)}
        ui.setContent {ConsoleTheme {ConsoleApp(state)}}
        if(pair) {
            ui.onNodeWithText("Admin token").performTextInput("fixture-token-for-instrumented-test-only")
            ui.onNodeWithText("Connect securely").performClick()
            ui.waitUntil(10_000) {state.paired && state.analytics!=null && state.config!=null}
        }
    }
    private fun idle() { ui.waitUntil(10_000) {state.busy.isEmpty()};ui.waitForIdle() }
    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(400)
        ui.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(300) // Let the emulator compositor present the completed frame.
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if(Build.VERSION.SDK_INT >= 29) {
            // Public test media survives AGP uninstalling the fixture app after instrumentation.
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png")
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ZeroChillAdminReview")
            }
            val uri=checkNotNull(app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
            checkNotNull(app.contentResolver.openOutputStream(uri)).use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        } else {
            val dir=File(app.getExternalFilesDir(null),"console-review").apply {mkdirs()}
            File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @After fun clean() {if(::state.isInitialized) ui.runOnUiThread {state.lock()}}
    @Test fun completeConsoleWithFixtureData() {
        launch(false);screenshot("01-pairing")
        ui.onNodeWithText("Admin token").performTextInput("fixture-token-for-instrumented-test-only")
        ui.onNodeWithText("Connect securely").performClick()
        ui.waitUntil(10_000) {state.paired && state.config!=null && state.analytics!=null};idle()
        screenshot("02-home")

        ui.onNodeWithText("Community",useUnmergedTree=true).performClick()
        ui.waitUntil(10_000) {state.communityAccounts.isNotEmpty()}
        ui.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Fixture User"))
        screenshot("03-community-accounts")
        ui.onNodeWithText("Fixture User").performClick()
        ui.waitUntil(10_000) {state.accountDetail != null};idle()
        ui.onNodeWithText("SOCIAL TOTALS").assertExists();screenshot("03b-community-account")
        ui.onNodeWithText("Close").performClick()
        ui.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Social"))
        ui.onNodeWithText("Social").performClick()
        ui.waitUntil(10_000) {state.communityActivity.isNotEmpty()}
        ui.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Fixture User commented"))
        ui.onNodeWithText("Fixture User commented").assertExists();screenshot("03c-community-social")
        ui.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Reports"))
        ui.onNodeWithText("Reports").performClick()
        ui.waitUntil(10_000) {state.communityReports.isNotEmpty()}
        ui.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("reported",substring=true))
        ui.onNodeWithText("reported",substring=true).assertExists();screenshot("03d-community-reports")

        ui.onNodeWithText("Feedback",useUnmergedTree=true).performClick();idle();screenshot("04-inbox")
        ui.onNodeWithText("Search messages, devices, versions").performTextInput("nothing matches")
        ui.onNodeWithText("No matching threads").assertExists();screenshot("04b-no-results")
        ui.onNodeWithContentDescription("Clear search").performClick()
        ui.onNodeWithText("Playback stutters after rotating the phone.").performClick();idle();screenshot("05-thread")
        ui.onNodeWithText("Details",substring=true).performClick();screenshot("06-thread-details")
        ui.onNodeWithText("New",useUnmergedTree=true).performClick()
        ui.onNodeWithText("In Progress").performClick();idle()
        ui.waitUntil(10_000) {state.thread?.item?.status=="in_progress"}
        ui.onNodeWithText("Reply to user…").performTextInput("Thanks. The fix is ready for testing.")
        ui.onNodeWithContentDescription("Send reply").performClick();idle()
        assertEquals(1,fake.replies)
        ui.onNodeWithContentDescription("Thread actions").performClick()
        ui.onNodeWithText("Delete thread").performClick()
        ui.onNodeWithText("Delete permanently").assertExists();assertFalse(fake.deleted)
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithContentDescription("Thread actions").performClick()
        ui.onNodeWithText("Delete thread").performClick()
        ui.onNodeWithText("Delete permanently").performClick();idle();assertTrue(fake.deleted)
        ui.onNodeWithText("Sources",useUnmergedTree=true).performClick();idle();screenshot("07-sources")
        ui.onNodeWithText("Publish").assertIsNotEnabled()
        ui.onNodeWithTag("source-list").performScrollToNode(hasContentDescription("Fapello enabled"))
        ui.onNodeWithText("Fapello").performClick();screenshot("07b-source-editor")
        ui.onNodeWithContentDescription("Close editor").performClick()
        ui.onNodeWithContentDescription("Fapello enabled").performClick()
        ui.onNodeWithText("Validate").performClick();idle()
        ui.onNodeWithText("Publish").assertIsEnabled()
        screenshot("08-candidate-validated")
        ui.onNodeWithTag("source-list").performScrollToNode(hasContentDescription("Fapello enabled"))
        ui.onNodeWithContentDescription("Fapello enabled").performClick()
        ui.onNodeWithText("Publish").assertIsNotEnabled()
        ui.onNodeWithText("Validate").performClick();idle()
        ui.onNodeWithText("Publish").performClick()
        ui.onNodeWithText("Publish version").performClick();idle();assertEquals(1,fake.publishes)
        ui.onNodeWithTag("source-list").performScrollToIndex(0)
        ui.onNodeWithText("History").performClick();idle();screenshot("09-history")
        ui.onNodeWithContentDescription("Close history").performClick()
        ui.onNodeWithText("Analytics",useUnmergedTree=true).performClick();idle();screenshot("10-analytics")
        ui.onNodeWithContentDescription("Admin settings").performClick()
        ui.onNodeWithText("Lock session").performClick();ui.onNodeWithText("Lock",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Connect securely").assertExists();assertEquals("",SecureTokenStore.read(app))
    }
    @Test fun rejectedPairingKeepsTokenStoreEmpty() {
        launch(false);fake.rejectPairing=true
        ui.onNodeWithText("Admin token").performTextInput("fixture-token-for-instrumented-test-only")
        ui.onNodeWithText("Connect securely").performClick();idle()
        assertFalse(state.paired);assertEquals("",SecureTokenStore.read(app))
        ui.onNodeWithText("Authentication failed. Check your admin token.").assertExists()
        screenshot("11-pairing-error")
    }
    @Test fun savedStateRestoresCandidateAndDraftButNeverValidationPermission() {
        launch();idle()
        val token=SecureTokenStore.read(app)
        assertEquals("fixture-token-for-instrumented-test-only",token)
        ui.runOnUiThread {
            state.navigate("sources")
            state.editConfig(JSONObject(state.config!!.text).apply {getJSONObject("sources").getJSONObject("fapello").put("enabled",false)})
            state.validateConfig()
        }
        idle();assertTrue(state.config!!.canPublish)
        ui.runOnUiThread {
            state.setDraft("fixture-feedback","Keep this draft")
            val restored=ConsoleState(app,saved,fake)
            assertTrue(restored.paired);assertEquals("sources",restored.destination)
            assertTrue(restored.config!!.dirty);assertFalse(restored.config!!.canPublish)
            assertFalse(JSONObject(restored.config!!.text).getJSONObject("sources").getJSONObject("fapello").getBoolean("enabled"))
            assertEquals("Keep this draft",restored.draft("fixture-feedback"))
        }
    }
    @Test fun lateStatusUpdateDoesNotOverwriteAnotherThread() {
        launch();idle()
        ui.runOnUiThread {state.openThread("fixture-feedback")};idle()
        val gate=CountDownLatch(1);fake.statusGate=gate
        ui.runOnUiThread {
            state.updateStatus("completed")
            state.closeThread()
            state.openThread("other-feedback")
        }
        ui.waitUntil(10_000) {state.thread?.item?.id=="other-feedback"}
        gate.countDown();idle()
        assertEquals("other-feedback",state.threadId)
        assertEquals("submitted",state.threadStatus)
    }
    @Test fun failedStatusRetainsConfirmedStatusAndExpiredAuthReturnsToPairing() {
        launch();idle()
        ui.runOnUiThread {state.openThread("fixture-feedback")};idle()
        fake.failStatus=true
        ui.runOnUiThread {state.updateStatus("completed")};idle()
        assertEquals("submitted",state.thread!!.item.status)
        assertTrue(state.errors.containsKey("threadAction"))
        fake.expired=true
        ui.runOnUiThread {state.loadAnalytics()};idle()
        assertFalse(state.paired);assertEquals("",SecureTokenStore.read(app))
        ui.onNodeWithText("Connect securely").assertExists()
    }
}
private class FixtureGateway : AdminGateway {
    var status="submitted";var replies=0;var publishes=0;var deleted=false;var failStatus=false;var expired=false;var rejectPairing=false;var statusGate:CountDownLatch?=null
    private val messages=mutableListOf(AdminRepository.FeedbackMessage(JSONObject("""{"id":"m1","feedback_id":"fixture-feedback","sender":"user","message":"Playback stutters after rotating the phone.","created_at":"2026-10-20T04:10:00Z","read_at":"2026-10-20T04:15:00Z"}""")),
        AdminRepository.FeedbackMessage(JSONObject("""{"id":"m2","feedback_id":"fixture-feedback","sender":"developer","message":"Thanks for the report. I am checking the player handoff.","created_at":"2026-10-20T04:18:00Z","read_at":"2026-10-20T04:20:00Z"}""")))
    private fun item(id:String="fixture-feedback")=AdminRepository.Item(JSONObject("""{"id":"$id","type":"bug_report","message":"Playback stutters after rotating the phone.","last_message":"Playback stutters after rotating the phone.","status":"${if(id=="fixture-feedback") status else "submitted"}","app_version":"5.0.1","android_version":"16","device":"OnePlus 13 / CPH2655","section":"shittok","created_at":"2026-10-20T04:10:00Z","last_message_at":"2026-10-20T04:10:00Z","unread_count":2}"""))
    private fun communityAccount()=CommunityRepository.Account(JSONObject("""{"id":"b20aa450-3550-4a10-9a77-000000000001","email":"fixture@example.com","username":"fixture","display_name":"Fixture User","bio":"Fixture profile","status":"active","created_at":"2026-10-19T12:00:00Z","last_sign_in_at":"2026-10-20T04:20:00Z","email_confirmed_at":"2026-10-19T12:02:00Z"}"""))
    override fun list(token:String):List<AdminRepository.Item> {if(rejectPairing) throw SecurityException("Rejected fixture");return if(deleted) emptyList() else listOf(item())}
    override fun analytics(token:String):AdminRepository.AnalyticsDashboard {
        if(expired) throw SecurityException("Expired")
        return AdminRepository.AnalyticsDashboard(JSONObject("""{"active_users":{"daily":128,"weekly":426,"monthly":1248},"generated_at":"2026-10-20T04:25:00Z","creators":[{"value":"Creator A","unique_users":162,"event_count":713},{"value":"Creator B","unique_users":98,"event_count":341},{"value":"Creator C","unique_users":64,"event_count":190}],"sections":[{"value":"shittok","unique_users":318,"event_count":6200},{"value":"onlyfap","unique_users":267,"event_count":3100},{"value":"shows","unique_users":104,"event_count":580}],"sources":[{"value":"kaotic","unique_users":240,"event_count":2000},{"value":"fapello","unique_users":170,"event_count":1410}],"versions":[{"value":"5.0.1","unique_users":310,"event_count":2000},{"value":"5.0.0","unique_users":116,"event_count":560}],"device_models":[{"value":"CPH2655","unique_users":38,"users_today":12,"event_count":470}],"device_manufacturers":[{"value":"Samsung","unique_users":180,"users_today":44,"event_count":1600}],"android_versions":[{"value":"16","unique_users":245,"users_today":95,"event_count":2800}]}"""))
    }
    override fun communityOverview(token:String)=CommunityRepository.Overview(JSONObject("""{"accounts":443,"new_accounts_7d":169,"comments":96,"video_likes":2416,"comment_likes":9,"messages":70,"reports":1,"blocks":2}"""))
    override fun accounts(token:String,query:String,filter:String)=listOf(communityAccount()).filter {query.isBlank() || it.name().contains(query,true)}
    override fun accountDetail(token:String,userId:String)=CommunityRepository.AccountDetail(JSONObject("""{
        "account":{"id":"b20aa450-3550-4a10-9a77-000000000001","email":"fixture@example.com","username":"fixture","display_name":"Fixture User","bio":"Fixture profile","status":"active","created_at":"2026-10-19T12:00:00Z","last_sign_in_at":"2026-10-20T04:20:00Z","email_confirmed_at":"2026-10-19T12:02:00Z"},
        "metrics":{"comments":4,"video_likes":11,"comment_likes":2,"messages_sent":3,"messages_received":5,"reports_filed":0,"reports_received":1,"blocks_made":1,"blocks_received":0},
        "recent_comments":[{"type":"comment","id":"c1","user_id":"b20aa450-3550-4a10-9a77-000000000001","display_name":"Fixture User","text":"Great clip","secondary":"Fixture Video","created_at":"2026-10-20T04:30:00Z","state":"comment"}],
        "recent_likes":[{"type":"video_like","id":"v1","user_id":"b20aa450-3550-4a10-9a77-000000000001","display_name":"Fixture User","secondary":"video-key","created_at":"2026-10-20T04:31:00Z","state":"liked"}],
        "recent_messages":[{"type":"message","id":"dm1","user_id":"b20aa450-3550-4a10-9a77-000000000001","display_name":"Fixture User","secondary":"To Other User","created_at":"2026-10-20T04:32:00Z","state":"read"}],
        "recent_reports":[{"id":"r1","reporter_id":"b20aa450-3550-4a10-9a77-000000000002","reporter_name":"Other User","reported_user_id":"b20aa450-3550-4a10-9a77-000000000001","reported_name":"Fixture User","reason":"spam","details":"Fixture report","created_at":"2026-10-20T04:33:00Z"}]
    }"""))
    override fun socialActivity(token:String,query:String,filter:String)=listOf(CommunityRepository.SocialActivity(JSONObject("""{"type":"comment","id":"c1","user_id":"b20aa450-3550-4a10-9a77-000000000001","display_name":"Fixture User","text":"Great clip","secondary":"Fixture Video","created_at":"2026-10-20T04:30:00Z","state":"comment"}""")))
    override fun reports(token:String,query:String,filter:String)=listOf(CommunityRepository.Report(JSONObject("""{"id":"r1","reporter_id":"b20aa450-3550-4a10-9a77-000000000002","reporter_name":"Other User","reported_user_id":"b20aa450-3550-4a10-9a77-000000000001","reported_name":"Fixture User","reason":"spam","details":"Fixture report","created_at":"2026-10-20T04:33:00Z"}""")))
    private var config=JSONObject("""{"configVersion":12,"updatedAt":"2026-10-19T12:00:00Z","global":{"sourceKillSwitchesEnabled":true,"fallbacksEnabled":true},"sources":{"fapello":{"enabled":true,"baseUrl":"https://example.com","requestTimeoutMs":12000,"retryCount":2,"headers":{"Accept":"text/html"}},"kaotic":{"enabled":true,"baseUrl":"https://example.org","requestTimeoutMs":15000,"retryCount":1},"bunkr":{"enabled":false,"indexUrl":"https://example.net","requestTimeoutMs":10000,"retryCount":2}}}""")
    override fun currentConfig(token:String)=JSONObject().put("config_version",config.getLong("configVersion")).put("config",JSONObject(config.toString()))
    override fun configHistory(token:String)=listOf(AdminRepository.ConfigVersion(JSONObject().put("config_version",config.getLong("configVersion")).put("based_on_version",12).put("is_active",true).put("updated_at","2026-10-20T04:30:00Z")))
    override fun validateConfig(token:String,config:JSONObject)=config.getLong("configVersion")
    override fun publishConfig(token:String,config:JSONObject):Long {publishes++;this.config=JSONObject(config.toString());return config.getLong("configVersion")}
    override fun rollbackConfig(token:String,version:Long)=version+1
    override fun thread(token:String,id:String)=AdminRepository.FeedbackThread(item(id),messages.toList())
    override fun reply(token:String,id:String,status:String,message:String) {replies++;messages+=AdminRepository.FeedbackMessage(JSONObject().put("id","reply-$replies").put("sender","developer").put("message",message).put("created_at","2026-10-20T04:30:00Z"))}
    override fun updateStatus(token:String,id:String,status:String) {if(failStatus) throw IllegalStateException("Fixture status failure");check(statusGate?.await(8,TimeUnit.SECONDS)!=false);this.status=status}
    override fun deleteFeedback(token:String,id:String) {deleted=true}
}
