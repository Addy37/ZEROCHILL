package com.addy37.crazyshitadmin

import android.app.Application
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
        ui.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir=File(app.getExternalFilesDir(null),"console-review").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @After fun clean() {if(::state.isInitialized) ui.runOnUiThread {state.lock()}}
    @Test fun completeConsoleWithFixtureData() {
        launch(false);screenshot("01-pairing")
        ui.onNodeWithText("Admin token").performTextInput("fixture-token-for-instrumented-test-only")
        ui.onNodeWithText("Connect securely").performClick()
        ui.waitUntil(10_000) {state.paired && state.config!=null && state.analytics!=null};idle()
        screenshot("02-home")
        ui.onNodeWithText("Feedback",useUnmergedTree=true).performClick();idle();screenshot("03-inbox")
        ui.onNodeWithText("Search messages, devices, versions").performTextInput("nothing matches")
        ui.onNodeWithText("No matching threads").assertExists()
        ui.onNodeWithContentDescription("Clear search").performClick()
        ui.onNodeWithText("Playback stutters after rotating the phone.").performClick();idle();screenshot("04-thread")
        ui.onNodeWithText("Details",substring=true).performClick();screenshot("05-thread-details")
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
        ui.onNodeWithContentDescription("Back").performClick();idle()
        ui.onNodeWithText("Sources",useUnmergedTree=true).performClick();idle();screenshot("06-sources")
        ui.onNodeWithText("Publish",useUnmergedTree=true).assertIsNotEnabled()
        ui.onNodeWithContentDescription("Fapello enabled").performScrollTo().performClick()
        ui.onNodeWithText("Validate",useUnmergedTree=true).performClick();idle()
        ui.onNodeWithText("Publish",useUnmergedTree=true).assertIsEnabled()
        screenshot("07-candidate-validated")
        ui.onNodeWithText("Publish",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Publish version").performClick();idle();assertEquals(1,fake.publishes)
        ui.onNodeWithText("History").performClick();idle();screenshot("08-history")
        ui.onNodeWithContentDescription("Close history").performClick()
        ui.onNodeWithText("Analytics",useUnmergedTree=true).performClick();idle();screenshot("09-analytics")
        ui.onNodeWithContentDescription("Admin settings").performClick()
        ui.onNodeWithText("Lock session").performClick();ui.onNodeWithText("Lock",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Connect securely").assertExists();assertEquals("",SecureTokenStore.read(app))
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
    var status="submitted";var replies=0;var publishes=0;var deleted=false;var failStatus=false;var expired=false
    private val messages=mutableListOf(AdminRepository.FeedbackMessage(JSONObject("""{"id":"m1","feedback_id":"fixture-feedback","sender":"user","message":"Playback stutters after rotating the phone.","created_at":"2026-10-20T04:10:00Z","read_at":"2026-10-20T04:15:00Z"}""")),
        AdminRepository.FeedbackMessage(JSONObject("""{"id":"m2","feedback_id":"fixture-feedback","sender":"developer","message":"Thanks for the report. I am checking the player handoff.","created_at":"2026-10-20T04:18:00Z","read_at":"2026-10-20T04:20:00Z"}""")))
    private fun item()=AdminRepository.Item(JSONObject("""{"id":"fixture-feedback","type":"bug_report","message":"Playback stutters after rotating the phone.","last_message":"Playback stutters after rotating the phone.","status":"$status","app_version":"5.0.1","android_version":"16","device":"OnePlus 13 / CPH2655","section":"shittok","created_at":"2026-10-20T04:10:00Z","last_message_at":"2026-10-20T04:10:00Z","unread_count":2}"""))
    override fun list(token:String)=listOf(item())
    override fun analytics(token:String):AdminRepository.AnalyticsDashboard {
        if(expired) throw SecurityException("Expired")
        return AdminRepository.AnalyticsDashboard(JSONObject("""{"active_users":{"daily":128,"weekly":426,"monthly":1248},"generated_at":"2026-10-20T04:25:00Z","creators":[{"value":"Creator A","unique_users":162,"event_count":713},{"value":"Creator B","unique_users":98,"event_count":341},{"value":"Creator C","unique_users":64,"event_count":190}],"sections":[{"value":"shittok","unique_users":318,"event_count":6200},{"value":"onlyfap","unique_users":267,"event_count":3100},{"value":"shows","unique_users":104,"event_count":580}],"sources":[{"value":"kaotic","unique_users":240,"event_count":2000},{"value":"fapello","unique_users":170,"event_count":1410}],"versions":[{"value":"5.0.1","unique_users":310,"event_count":2000},{"value":"5.0.0","unique_users":116,"event_count":560}],"device_models":[{"value":"CPH2655","unique_users":38,"users_today":12,"event_count":470}],"device_manufacturers":[{"value":"Samsung","unique_users":180,"users_today":44,"event_count":1600}],"android_versions":[{"value":"16","unique_users":245,"users_today":95,"event_count":2800}]}"""))
    }
    private var config=JSONObject("""{"configVersion":12,"updatedAt":"2026-10-19T12:00:00Z","global":{"sourceKillSwitchesEnabled":true,"fallbacksEnabled":true},"sources":{"fapello":{"enabled":true,"baseUrl":"https://example.com","requestTimeoutMs":12000,"retryCount":2,"headers":{"Accept":"text/html"}},"kaotic":{"enabled":true,"baseUrl":"https://example.org","requestTimeoutMs":15000,"retryCount":1},"bunkr":{"enabled":false,"indexUrl":"https://example.net","requestTimeoutMs":10000,"retryCount":2}}}""")
    override fun currentConfig(token:String)=JSONObject().put("config_version",config.getLong("configVersion")).put("config",JSONObject(config.toString()))
    override fun configHistory(token:String)=listOf(AdminRepository.ConfigVersion(JSONObject().put("config_version",config.getLong("configVersion")).put("based_on_version",12).put("is_active",true).put("updated_at","2026-10-20T04:30:00Z")))
    override fun validateConfig(token:String,config:JSONObject)=config.getLong("configVersion")
    override fun publishConfig(token:String,config:JSONObject):Long {publishes++;this.config=JSONObject(config.toString());return config.getLong("configVersion")}
    override fun rollbackConfig(token:String,version:Long)=version+1
    override fun thread(token:String,id:String)=AdminRepository.FeedbackThread(item(),messages.toList())
    override fun reply(token:String,id:String,status:String,message:String) {replies++;messages+=AdminRepository.FeedbackMessage(JSONObject().put("id","reply-$replies").put("sender","developer").put("message",message).put("created_at","2026-10-20T04:30:00Z"))}
    override fun updateStatus(token:String,id:String,status:String) {if(failStatus) throw IllegalStateException("Fixture status failure");this.status=status}
    override fun deleteFeedback(token:String,id:String) {deleted=true}
}
