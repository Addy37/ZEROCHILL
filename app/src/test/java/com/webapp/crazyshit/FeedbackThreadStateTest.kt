package com.webapp.crazyshit

import org.junit.Assert.*
import org.junit.Test

class FeedbackThreadStateTest {
    private class Gateway : FeedbackThreadGateway {
        val loads = mutableListOf<(FeedbackConversationUi?, Exception?) -> Unit>()
        var sendDone: ((Exception?) -> Unit)? = null
        var replies = 0
        override fun load(id: String, done: (FeedbackConversationUi?, Exception?) -> Unit) { loads += done }
        override fun reply(id: String, message: String, done: (Exception?) -> Unit) { replies++; sendDone = done }
    }
    private val conversation = FeedbackConversationUi("bug_report", "reviewing", listOf(
        FeedbackMessageUi("message-1", true, "Can you try again?", "Oct 20", "")))

    @Test fun newerRefreshWinsAndKeepsReplyDraft() {
        val gateway = Gateway()
        val state = FeedbackThreadState(gateway)
        state.initialize("thread-1", "My reply")
        state.load()
        gateway.loads.last()(conversation, null)
        gateway.loads.first()(null, Exception("old failure"))
        assertEquals(conversation, state.ui.conversation)
        assertEquals("My reply", state.ui.draft)
        assertNull(state.ui.error)
        assertFalse(state.ui.loading)
    }

    @Test fun failedReplyPreservesDraftAndAvoidsDuplicateSend() {
        val gateway = Gateway()
        val state = FeedbackThreadState(gateway)
        state.initialize("thread-1", "I tested the fix")
        state.send(); state.send()
        state.edit("Should not replace an in-flight draft")
        assertEquals(1, gateway.replies)
        gateway.sendDone!!(Exception("Offline"))
        assertEquals("I tested the fix", state.ui.draft)
        assertFalse(state.ui.sending)
        assertEquals("Offline", state.ui.error)
    }

    @Test fun successfulReplySupersedesPreSendRefresh() {
        val gateway = Gateway()
        val state = FeedbackThreadState(gateway)
        state.initialize("thread-1", "The fix works")
        state.send()
        gateway.sendDone!!(null)
        assertEquals("", state.ui.draft)
        assertEquals(2, gateway.loads.size)
        gateway.loads.first()(null, Exception("stale"))
        assertTrue(state.ui.loading)
        assertNull(state.ui.error)
        gateway.loads.last()(conversation, null)
        assertEquals(conversation, state.ui.conversation)
    }

    @Test fun initializationAfterRotationKeepsInflightRequestAndDraft() {
        val gateway = Gateway()
        val state = FeedbackThreadState(gateway)
        state.initialize("thread-1", "Keep this")
        state.send()
        state.initialize("thread-1", "Old saved value")
        assertEquals("Keep this", state.ui.draft)
        assertTrue(state.ui.sending)
        assertEquals(1, gateway.loads.size)
    }

    @Test fun blankReplyDoesNotSendAndInputRemainsBounded() {
        val gateway = Gateway()
        val state = FeedbackThreadState(gateway)
        state.initialize("thread-1", "  ")
        state.send()
        assertEquals(0, gateway.replies)
        assertEquals("Type a reply.", state.ui.validation)
        state.edit("a".repeat(2200))
        assertEquals(2000, state.ui.draft.length)
    }
}
