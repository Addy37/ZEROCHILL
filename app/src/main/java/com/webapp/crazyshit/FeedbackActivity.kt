package com.webapp.crazyshit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webapp.crazyshit.ui.*
import java.util.Locale

class FeedbackActivity : ComponentActivity() {
    private lateinit var screen: FeedbackState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZeroChillUi.applySystemBars(this)
        screen = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FeedbackState(RepositoryFeedbackGateway(applicationContext), FeedbackRepository.isConfigured()) as T
        })[FeedbackState::class.java]
        screen.initialize(intent.getStringExtra(EXTRA_SOURCE), intent.getIntExtra(EXTRA_PRESELECTED_RATING, 0), savedInstanceState)
        installZeroChillContent {
            val state = screen.ui
            LaunchedEffect(state.notice) {
                state.notice?.let { ZeroChillToast.makeText(this@FeedbackActivity, it, ZeroChillToast.LENGTH_LONG).show() }
                screen.consumeNotice()
            }
            FeedbackScreen(state, screen, ::finish) { id ->
                startActivity(Intent(this, FeedbackThreadActivity::class.java)
                    .putExtra(FeedbackThreadActivity.EXTRA_FEEDBACK_ID, id))
            }
        }
    }

    override fun onResume() { super.onResume(); screen.refresh() }
    override fun onSaveInstanceState(outState: Bundle) { screen.save(outState); super.onSaveInstanceState(outState) }

    companion object {
        const val EXTRA_PRESELECTED_RATING = "preselected_rating"
        const val EXTRA_SOURCE = "feedback_source"
    }
}

@Composable
internal fun FeedbackScreen(state: FeedbackUiState, events: FeedbackState, onBack: () -> Unit, onThread: (String) -> Unit) {
    val composeScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()
    ZeroChillScreen("Feedback", onBack) {
        PrimaryTabRow(selectedTabIndex = if (state.history) 1 else 0,
            containerColor = MaterialTheme.colorScheme.background) {
            Tab(!state.history, { events.showHistory(false) }, enabled = !state.sending,
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text("Send feedback") })
            Tab(state.history, { events.showHistory(true) }, enabled = !state.sending,
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text("My feedback") })
        }
        LazyColumn(state = if (state.history) historyScroll else composeScroll,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.history) {
                when {
                    !state.configured -> item { ZeroChillEmptyState("Feedback unavailable", "Feedback is not connected yet.") }
                    state.loading -> item { ZeroChillLoading("Loading your feedback…") }
                    state.error != null -> item { ZeroChillEmptyState("Couldn't load feedback", state.error,
                        "Try again", events::refresh) }
                    state.items.isEmpty() -> item { ZeroChillEmptyState("No feedback yet", "You have not sent any feedback from this device.") }
                    else -> items(state.items, key = { it.id }) { item -> FeedbackCard(item) { onThread(item.id) } }
                }
            } else {
                item(key = "intro") { Text("Tell me what works, what is broken, or what you want added.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                item(key = "type") {
                    ZeroChillSectionHeader("What would you like to do?")
                    ZeroChillPanel {
                        listOf("feature_request" to "Suggest a feature", "bug_report" to "Report a problem",
                            "general_feedback" to "General feedback").forEach { (type, title) ->
                            Row(Modifier.fillMaxWidth().selectable(state.type == type, enabled = !state.sending,
                                role = Role.RadioButton, onClick = { events.selectType(type) }).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(state.type == type, null, enabled = !state.sending)
                                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
                item(key = "message") {
                    OutlinedTextField(state.message, events::edit, Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        enabled = !state.sending, label = { Text("Your message") }, placeholder = { Text("What should I know?") },
                        isError = state.validation != null, shape = MaterialTheme.shapes.medium,
                        supportingText = { Text(state.validation ?: "${state.message.length}/2000") })
                }
                item(key = "privacy") { Text("No account or name is required. The app includes its version, your phone model, and Android version so bug reports are easier to check.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item(key = "rating") {
                    ZeroChillSectionHeader("Optional rating")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        for (rating in 1..5) {
                            TextButton({ events.rate(rating) }, enabled = !state.sending,
                                contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f)
                                    .heightIn(min = 48.dp).semantics { contentDescription = "$rating out of 5 stars" }) {
                                Text(if (rating <= state.rating) "★" else "☆", style = MaterialTheme.typography.headlineLarge,
                                    color = if (rating <= state.rating) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item(key = "submit") {
                    ZeroChillButton(if (state.sending) "Sending…" else "Send feedback",
                        state.configured && !state.sending, Modifier.fillMaxWidth(), events::submit)
                    if (!state.configured) Text("Feedback is not connected yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(item: FeedbackPreview, onClick: () -> Unit) {
    ZeroChillPanel(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(feedbackTypeLabel(item.type), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium)
            Text(feedbackStatusLabel(item.status), color = feedbackStatusColor(item.status), style = MaterialTheme.typography.labelSmall)
            if (item.unread > 0) Text(if (item.unread == 1) "NEW REPLY" else "${item.unread} NEW REPLIES",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(if (item.fromDeveloper) "ZEROCHILL" else "YOU", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.preview, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (item.rating > 0) Text("★".repeat(item.rating), color = MaterialTheme.colorScheme.primary)
        }
    }
}

internal fun feedbackTypeLabel(type: String) = when (type) {
    "feature_request" -> "Feature request"
    "bug_report" -> "Bug report"
    else -> "General feedback"
}
internal fun feedbackStatusLabel(status: String?) = status.orEmpty().ifBlank { "submitted" }
    .replace('_', ' ').uppercase(Locale.US)

@Composable
internal fun feedbackStatusColor(status: String) = when (status) {
    "completed" -> colorResource(R.color.zc_success)
    "planned", "in_progress" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
