package com.webapp.crazyshit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webapp.crazyshit.ui.*

class FeedbackThreadActivity : ComponentActivity() {
    private var screen: FeedbackThreadState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_FEEDBACK_ID)?.trim().orEmpty()
        if (id.isEmpty()) { finish(); return }
        ZeroChillUi.applySystemBars(this)
        val state = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FeedbackThreadState(RepositoryFeedbackThreadGateway(applicationContext)) as T
        })[FeedbackThreadState::class.java]
        screen = state
        state.initialize(id, savedInstanceState?.getString("feedback_reply").orEmpty())
        installZeroChillContent { FeedbackThreadScreen(state.ui, state, ::finish) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("feedback_reply", screen?.ui?.draft)
        super.onSaveInstanceState(outState)
    }
    companion object { const val EXTRA_FEEDBACK_ID = "feedback_id" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedbackThreadScreen(state: FeedbackThreadUiState, events: FeedbackThreadState, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val conversation = state.conversation
    LaunchedEffect(state.revision) {
        if (!conversation?.messages.isNullOrEmpty()) listState.scrollToItem(conversation!!.messages.lastIndex)
    }
    ZeroChillScreen(conversation?.let { feedbackTypeLabel(it.type) } ?: "Feedback conversation", onBack) {
        if (conversation != null) Text(feedbackStatusLabel(conversation.status),
            color = feedbackStatusColor(conversation.status), style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        if (state.error != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(events::load) { Text("Refresh") }
            }
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = events::load,
            modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (conversation != null) {
                    if (conversation.messages.isEmpty()) item { ZeroChillEmptyState("No messages yet", "Pull down to refresh this conversation.") }
                    else items(conversation.messages, key = { it.id }) { message -> FeedbackBubble(message) }
                } else if (!state.loading && state.error != null) item {
                    ZeroChillEmptyState("Conversation unavailable", "Pull down or tap Refresh to try again.")
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.draft, events::edit, modifier = Modifier.fillMaxWidth(),
                label = { Text("Reply to ZEROCHILL") }, enabled = !state.sending, maxLines = 5,
                shape = MaterialTheme.shapes.medium, isError = state.validation != null,
                supportingText = { Text(state.validation ?: "${state.draft.length}/2000") })
            ZeroChillButton(if (state.sending) "Sending…" else "Send", !state.sending,
                Modifier.align(Alignment.End), events::send)
        }
    }
}

@Composable
private fun FeedbackBubble(message: FeedbackMessageUi) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.developer) Alignment.CenterStart else Alignment.CenterEnd) {
        Surface(shape = MaterialTheme.shapes.medium,
            color = if (message.developer) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 340.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (message.developer) "ZEROCHILL" else "YOU", style = MaterialTheme.typography.labelSmall,
                    color = if (message.developer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                SelectionContainer { Text(message.message, style = MaterialTheme.typography.bodyLarge) }
                Text(message.created, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!message.developer && message.seen.isNotEmpty()) Text("Seen by ZEROCHILL · ${message.seen}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
