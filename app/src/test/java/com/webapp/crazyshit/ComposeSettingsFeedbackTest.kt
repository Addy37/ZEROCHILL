package com.webapp.crazyshit

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeSettingsFeedbackTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun settingsToggleUsesExistingPreferenceAndRestoresAfterRecreation() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { host ->
            val prefs = host.get().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Haptic feedback"))
            compose.onNodeWithText("Haptic feedback").assertIsOn().performClick()
            assertFalse(prefs.getBoolean("haptics_enabled", true))
            host.recreate()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Haptic feedback"))
            compose.onNodeWithText("Haptic feedback").assertIsOff()
        }
    }

    @Test fun settingsActionsKeepExistingDestinations() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { host ->
            saveRender("compose-settings-phone", host.get())
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Favorite creators"))
            compose.onNodeWithText("Favorite creators").performClick()
            val started = org.robolectric.Shadows.shadowOf(host.get()).nextStartedActivity
            assertEquals(CreatorsActivity::class.java.name, started.component!!.className)
        }
    }

    @Test fun settingsChoicesAndDiagnosticsSurviveRotationWithoutRunningActions() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { host ->
            compose.onNodeWithText("Check frequency").performScrollTo().performClick()
            compose.onNodeWithText("Every 3 hours").assertExists()
            host.recreate()
            compose.onNodeWithText("Every 3 hours").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Performance details"))
            compose.onNodeWithText("Performance details").performClick()
            compose.onNodeWithText("Copy diagnostics").assertExists()
            compose.onNodeWithText("Close").performClick()
        }
    }

    @Test fun destructiveSettingsChoiceNeverExecutesOnRotationOrCancel() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { host ->
            val context = host.get()
            FavoriteStore.add(context, "Keep this", "https://fixture.invalid/keep")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Manage history & Watch Later"))
            compose.onNodeWithText("Manage history & Watch Later").performClick()
            compose.onNodeWithText("Clear Watch Later").performClick()
            compose.onNodeWithText("Clear Watch Later?").assertExists()
            host.recreate()
            compose.onNodeWithText("Clear Watch Later").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(1, FavoriteStore.load(host.get()).size)
        }
    }

    @Test fun settingsCopiesRetainedInventoryAndStartupEvidenceWithoutContentUrls() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { host ->
            ShitTokPoolDiagnostics.snapshot("usable=2 desired=10 tailHits=3 selectedState=3 https://private.test/video?token=secret")
            org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(host.get(), "showPerformanceDetails")
            compose.onNodeWithText("Copy diagnostics").performClick()
            val clipboard = host.get().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val copied = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertTrue(copied.contains("tailHits=3"))
            assertTrue(copied.contains("This app session"))
            assertTrue(copied.contains("Source:"))
            assertFalse(copied.contains("private.test"))
            assertFalse(copied.contains("secret"))
        }
    }

    @Test fun feedbackDraftAndRatingSurviveActivityRecreation() {
        val intent = Intent().putExtra(FeedbackActivity.EXTRA_SOURCE, "Player")
            .putExtra(FeedbackActivity.EXTRA_PRESELECTED_RATING, 4)
        Robolectric.buildActivity(FeedbackActivity::class.java, intent).setup().use { host ->
            val back = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithText("Feedback").fetchSemanticsNode().boundsInRoot
            assertTrue("Header title must leave the back button clear", title.left >= back.right)
            saveRender("compose-feedback-phone", host.get())
            compose.onNodeWithText("Your message").performScrollTo().performTextInput("Keep my feedback draft")
            host.recreate()
            compose.onNodeWithText("Keep my feedback draft").performScrollTo().assertExists()
            val out = Bundle()
            host.saveInstanceState(out)
            assertEquals("Keep my feedback draft", out.getString("feedback_draft"))
            assertEquals("Player", out.getString("feedback_section"))
            assertEquals(4, out.getInt("feedback_rating"))
        }
    }

    @Test fun feedbackTabsPreserveDraftWithoutCallingProduction() {
        val gateway = object : FeedbackGateway {
            override fun submit(type: String, message: String, rating: Int, section: String, done: (Exception?) -> Unit) {
                fail("This interaction must not submit feedback")
            }
            override fun list(done: (List<FeedbackPreview>?, Exception?) -> Unit) { done(emptyList(), null) }
            override fun submitted() {}
        }
        val state = FeedbackState(gateway, true)
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            host.get().installZeroChillContent { FeedbackScreen(state.ui, state, {}, {}) }
            compose.onNodeWithText("Your message").performScrollTo().performTextInput("Keep this across tabs")
            compose.onNodeWithText("My feedback").performClick()
            compose.onNodeWithText("No feedback yet").assertExists()
            compose.onNodeWithText("Send feedback").performClick()
            compose.onNodeWithText("Keep this across tabs").performScrollTo().assertExists()
        }
    }

    @Test fun conversationShowsReadReceiptAndRetainsReplyWhileRefreshing() {
        val gateway = object : FeedbackThreadGateway {
            override fun load(id: String, done: (FeedbackConversationUi?, Exception?) -> Unit) {
                done(FeedbackConversationUi("bug_report", "in_progress", listOf(
                    FeedbackMessageUi("1", true, "Can you try the latest build?", "Oct 20 · 2:00 PM", ""),
                    FeedbackMessageUi("2", false, "The latest build works.", "Oct 20 · 2:10 PM", "Oct 20 · 2:11 PM"))), null)
            }
            override fun reply(id: String, message: String, done: (Exception?) -> Unit) {}
        }
        val state = FeedbackThreadState(gateway)
        state.initialize("test-thread", "")
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            host.get().installZeroChillContent { FeedbackThreadScreen(state.ui, state, {}) }
            compose.onNodeWithText("Seen by ZEROCHILL · Oct 20 · 2:11 PM").assertExists()
            saveRender("compose-feedback-thread-phone", host.get())
            compose.onNodeWithText("Reply to ZEROCHILL").performTextInput("I tested it again")
            state.load()
            compose.onNodeWithText("I tested it again").assertExists()
        }
    }

    @Test fun legacyResumeFittersLeaveComposeRootInsetsAndWidthAlone() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { host ->
            UiFoundationCoordinator.onActivityResumed(host.get())
            compose.waitForIdle()
            val root = host.get().findViewById<android.view.View>(R.id.zerochill_compose_root)
            assertEquals(0, root.paddingTop)
            assertEquals(0, root.paddingBottom)
            assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, root.layoutParams.width)
        }
    }

    private fun saveRender(name: String, activity: android.app.Activity) {
        compose.waitForIdle()
        val view = activity.findViewById<android.view.View>(android.R.id.content)
        val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        val directory = java.io.File("build/reports/visual-tests").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(directory, "$name.png")).use {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
