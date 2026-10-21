package com.webapp.crazyshit

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class FeedbackMessageUi(val id: String, val developer: Boolean, val message: String,
    val created: String, val seen: String)
internal data class FeedbackConversationUi(val type: String, val status: String, val messages: List<FeedbackMessageUi>)
internal data class FeedbackThreadUiState(val conversation: FeedbackConversationUi? = null,
    val loading: Boolean = false, val sending: Boolean = false, val draft: String = "",
    val validation: String? = null, val error: String? = null, val revision: Int = 0)

internal interface FeedbackThreadGateway {
    fun load(id: String, done: (FeedbackConversationUi?, Exception?) -> Unit)
    fun reply(id: String, message: String, done: (Exception?) -> Unit)
}

internal class RepositoryFeedbackThreadGateway(context: Context) : FeedbackThreadGateway {
    private val app = context.applicationContext
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    override fun load(id: String, done: (FeedbackConversationUi?, Exception?) -> Unit) {
        FeedbackRepository.thread(app, id) { thread, error ->
            val snapshot = thread?.let { value -> FeedbackConversationUi(value.item.type, value.item.status,
                value.messages.mapIndexed { index, message -> FeedbackMessageUi(
                    message.id.ifBlank { "message-$index" }, message.fromDeveloper(), message.message,
                    feedbackDate(message.createdAt), if (message.isRead()) feedbackDate(message.readAt) else "") }) }
            main.post { done(snapshot, error) }
        }
    }
    override fun reply(id: String, message: String, done: (Exception?) -> Unit) {
        FeedbackRepository.reply(app, id, message) { _, error -> main.post { done(error) } }
    }
}

internal class FeedbackThreadState(private val gateway: FeedbackThreadGateway) : ViewModel() {
    var ui by mutableStateOf(FeedbackThreadUiState())
        private set
    private var id = ""
    private var request = 0
    private var active = true

    fun initialize(feedbackId: String, draft: String) {
        if (id.isNotEmpty()) return
        id = feedbackId
        ui = ui.copy(draft = draft.take(2000))
        load()
    }
    fun edit(value: String) { if (!ui.sending) ui = ui.copy(draft = value.take(2000), validation = null) }
    fun load() {
        if (id.isEmpty()) return
        val generation = ++request
        ui = ui.copy(loading = true, error = null)
        gateway.load(id) { conversation, error ->
            if (active && request == generation) {
                ui = if (error != null || conversation == null) ui.copy(loading = false,
                    error = error?.message ?: "Couldn't load conversation.")
                else ui.copy(loading = false, conversation = conversation, error = null, revision = ui.revision + 1)
            }
        }
    }
    fun send() {
        if (ui.sending || id.isEmpty()) return
        val text = ui.draft.trim()
        if (text.isEmpty()) { ui = ui.copy(validation = "Type a reply."); return }
        ui = ui.copy(sending = true, validation = null, error = null)
        gateway.reply(id, text) { error ->
            if (active) {
                if (error != null) ui = ui.copy(sending = false, error = error.message ?: "Couldn't send reply.")
                else { ui = ui.copy(sending = false, draft = ""); load() }
            }
        }
    }
    override fun onCleared() { active = false; request++ }
}

internal fun feedbackDate(raw: String?): String {
    if (raw.isNullOrEmpty() || raw == "null") return ""
    return try {
        DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault()).format(Instant.parse(raw))
    } catch (_: Exception) { raw }
}
