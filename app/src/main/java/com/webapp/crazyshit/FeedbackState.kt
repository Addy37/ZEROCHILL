package com.webapp.crazyshit

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

internal data class FeedbackPreview(val id: String, val type: String, val status: String,
    val preview: String, val fromDeveloper: Boolean, val unread: Int, val rating: Int)

internal data class FeedbackUiState(
    val type: String = "feature_request", val message: String = "", val rating: Int = 0,
    val history: Boolean = false, val sending: Boolean = false, val loading: Boolean = false,
    val configured: Boolean = false, val validation: String? = null, val error: String? = null,
    val items: List<FeedbackPreview> = emptyList(), val notice: String? = null,
)

/** Application context only. Requests survive rotation without retaining a screen or its Views. */
internal interface FeedbackGateway {
    fun submit(type: String, message: String, rating: Int, section: String, done: (Exception?) -> Unit)
    fun list(done: (List<FeedbackPreview>?, Exception?) -> Unit)
    fun submitted()
}

internal class RepositoryFeedbackGateway(context: Context) : FeedbackGateway {
    private val app = context.applicationContext
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    override fun submit(type: String, message: String, rating: Int, section: String, done: (Exception?) -> Unit) {
        FeedbackRepository.submit(app, type, message, rating, section) { _, error -> main.post { done(error) } }
    }
    override fun list(done: (List<FeedbackPreview>?, Exception?) -> Unit) {
        FeedbackRepository.list(app) { items, error ->
            val snapshots = items?.map { FeedbackPreview(it.id, it.type, it.status,
                it.lastMessage.orEmpty().trim().take(220), it.lastSender == "developer", it.unreadCount,
                it.rating.coerceIn(0, 5)) }
            main.post { done(snapshots, error) }
        }
    }
    override fun submitted() { RatingFeedbackPrompt.markFeedbackSubmitted(app) }
}

internal class FeedbackState(private val gateway: FeedbackGateway, configured: Boolean) : ViewModel() {
    var ui by mutableStateOf(FeedbackUiState(configured = configured))
        private set
    private var initialized = false
    private var active = true
    private var historyRequest = 0
    private var section = "More"

    fun initialize(source: String?, initialRating: Int, saved: Bundle?) {
        if (initialized) return
        initialized = true
        section = saved?.getString("feedback_section") ?: source?.trim()?.takeIf { it.isNotEmpty() } ?: "More"
        ui = ui.copy(type = saved?.getString("feedback_type")
            ?: if (!source.isNullOrBlank()) "general_feedback" else "feature_request",
            message = saved?.getString("feedback_draft").orEmpty().take(2000),
            rating = (saved?.getInt("feedback_rating") ?: initialRating).coerceIn(0, 5),
            history = saved?.getBoolean("feedback_history") ?: false)
    }

    fun save(out: Bundle) {
        out.putString("feedback_section", section)
        out.putString("feedback_type", ui.type)
        out.putString("feedback_draft", ui.message)
        out.putInt("feedback_rating", ui.rating)
        out.putBoolean("feedback_history", ui.history)
    }

    fun edit(value: String) { if (!ui.sending) ui = ui.copy(message = value.take(2000), validation = null) }
    fun selectType(type: String) {
        if (!ui.sending && type in listOf("feature_request", "bug_report", "general_feedback")) ui = ui.copy(type = type)
    }
    fun rate(rating: Int) { if (!ui.sending) ui = ui.copy(rating = rating.coerceIn(0, 5)) }
    fun consumeNotice() { ui = ui.copy(notice = null) }

    fun showHistory(history: Boolean) {
        if (ui.sending) return
        historyRequest++
        ui = ui.copy(history = history, loading = false, error = null)
        if (history) refresh()
    }

    fun refresh() {
        if (!ui.history || !ui.configured || ui.loading) return
        val request = ++historyRequest
        ui = ui.copy(loading = true, error = null)
        gateway.list { items, error ->
            if (active && request == historyRequest && ui.history) {
                ui = ui.copy(loading = false, items = items.orEmpty().toList(),
                    error = if (error != null) "Couldn't load your feedback." else null)
            }
        }
    }

    fun submit() {
        if (ui.sending || !ui.configured) return
        val message = ui.message.trim()
        if (message.length < 5) { ui = ui.copy(validation = "Please add a little more detail."); return }
        ui = ui.copy(sending = true, validation = null, error = null)
        gateway.submit(ui.type, message, ui.rating, section) { error ->
            if (active) {
                if (error != null) ui = ui.copy(sending = false,
                    notice = "Couldn't send feedback. Try again.")
                else {
                    gateway.submitted()
                    ui = ui.copy(sending = false, message = "", rating = 0, notice = "Feedback sent. Thank you.")
                    showHistory(true)
                }
            }
        }
    }

    override fun onCleared() { active = false; historyRequest++ }
}
