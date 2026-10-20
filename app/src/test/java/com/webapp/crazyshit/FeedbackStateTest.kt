package com.webapp.crazyshit

import android.app.Application
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FeedbackStateTest {
    private class Gateway : FeedbackGateway {
        var sends = 0
        var submitted = 0
        var section = ""
        var sendDone: ((Exception?) -> Unit)? = null
        val loads = mutableListOf<(List<FeedbackPreview>?, Exception?) -> Unit>()
        override fun submit(type: String, message: String, rating: Int, section: String, done: (Exception?) -> Unit) {
            sends++; this.section = section; sendDone = done
        }
        override fun list(done: (List<FeedbackPreview>?, Exception?) -> Unit) { loads += done }
        override fun submitted() { submitted++ }
    }

    @Test fun draftRatingTypeAndSourceSurviveRecreationWithoutPreferenceChanges() {
        val gateway = Gateway()
        val state = FeedbackState(gateway, true)
        state.initialize("Player", 4, null)
        assertEquals("general_feedback", state.ui.type)
        state.edit("Rotation should keep this draft")
        state.selectType("bug_report")
        val bundle = Bundle().also(state::save)
        val restored = FeedbackState(gateway, true)
        restored.initialize(null, 0, bundle)
        assertEquals(state.ui, restored.ui)
        restored.submit()
        assertEquals("Player", gateway.section)
    }

    @Test fun doubleTapSendsOnceAndKeepsDraftOnFailure() {
        val gateway = Gateway()
        val state = FeedbackState(gateway, true)
        state.edit("This should not be lost")
        state.rate(3)
        state.submit(); state.submit()
        state.edit("An edit while sending")
        assertEquals(1, gateway.sends)
        assertEquals("This should not be lost", state.ui.message)
        gateway.sendDone!!(Exception("offline"))
        assertFalse(state.ui.sending)
        assertEquals("This should not be lost", state.ui.message)
        assertEquals(3, state.ui.rating)
        assertEquals(0, gateway.submitted)
    }

    @Test fun successMarksPromptAndOpensHistoryWithClearedDraft() {
        val gateway = Gateway()
        val state = FeedbackState(gateway, true)
        state.edit("A complete feedback message")
        state.rate(5); state.submit()
        gateway.sendDone!!(null)
        assertEquals(1, gateway.submitted)
        assertTrue(state.ui.history)
        assertTrue(state.ui.loading)
        assertEquals("", state.ui.message)
        assertEquals(0, state.ui.rating)
        gateway.loads.last()(emptyList(), null)
        assertFalse(state.ui.loading)
    }

    @Test fun lateHistoryResultCannotReplaceNewRequestOrComposer() {
        val gateway = Gateway()
        val state = FeedbackState(gateway, true)
        state.showHistory(true)
        val old = gateway.loads.last()
        state.showHistory(false)
        state.edit("A draft while history loads")
        state.showHistory(true)
        old(null, Exception("stale"))
        assertNull(state.ui.error)
        assertTrue(state.ui.loading)
        gateway.loads.last()(emptyList(), null)
        assertFalse(state.ui.loading)
        state.showHistory(false)
        assertEquals("A draft while history loads", state.ui.message)
    }

    @Test fun validationAndUnconfiguredStateNeverSend() {
        val gateway = Gateway()
        val state = FeedbackState(gateway, true)
        state.edit("  hi  "); state.submit()
        assertNotNull(state.ui.validation)
        state.edit("x".repeat(2100))
        assertEquals(2000, state.ui.message.length)
        val offline = FeedbackState(gateway, false)
        offline.edit("Enough detail"); offline.submit(); offline.showHistory(true)
        assertEquals(0, gateway.sends)
        assertTrue(gateway.loads.isEmpty())
    }

    @Test fun retainedStateInitializationDoesNotOverwriteAnInflightSendAfterRotation() {
        val gateway = Gateway()
        val state = FeedbackState(gateway, true)
        state.initialize("Search", 2, null)
        state.edit("Keep the same in-flight request"); state.submit()
        state.initialize("Library", 5, Bundle())
        assertEquals("Keep the same in-flight request", state.ui.message)
        assertTrue(state.ui.sending)
        assertEquals(2, state.ui.rating)
        assertEquals(1, gateway.sends)
        assertEquals("Search", gateway.section)
    }
}
