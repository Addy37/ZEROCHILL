package com.webapp.crazyshit

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.bumptech.glide.Glide
import com.webapp.crazyshit.ui.*
import java.util.function.Consumer
import java.util.function.Supplier

/** Collection controllers publish presentation snapshots without changing their storage contracts. */
internal class LibraryCompose(private val activity: ComponentActivity, private val title: String,
    private val onQuery: Consumer<String>) {
    class Card(val key: String, val version: String, val create: Supplier<View>)
    var searchable by mutableStateOf(true)
    var query by mutableStateOf("")
    var summary by mutableStateOf("")
    var emptyMessage by mutableStateOf("")
    var actionLabel by mutableStateOf("")
    var actionEnabled by mutableStateOf(true)
    var action: Runnable? = null
    var moreLabel by mutableStateOf("")
    var more: Runnable? = null
    private var cards by mutableStateOf(emptyList<Card>())
    fun showCards(value: List<Card>) { cards = value.toList() }
    fun install(content: View?) {
        activity.installZeroChillContent {
            ZeroChillScreen(title, activity::finish, actions = {
                if (actionLabel == "Section options" || actionLabel == "Find creators") {
                    IconButton(onClick = { action?.run() }, enabled = actionEnabled,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = actionLabel }) {
                        Text(if (actionLabel == "Section options") "⋮" else "+", fontSize = 24.sp)
                    }
                } else if (actionLabel.isNotEmpty()) TextButton(onClick = { action?.run() }, enabled = actionEnabled,
                    modifier = Modifier.semantics { contentDescription = actionLabel }) { Text("Refresh") }
            }) {
                if (searchable) OutlinedTextField(query, { query = it; onQuery.accept(it) }, singleLine = true,
                    label = { Text("Search $title") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                if (emptyMessage.isNotEmpty()) Text(emptyMessage,
                    modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (content != null) AndroidView(factory = { content }, modifier = Modifier.weight(1f).fillMaxWidth())
                else LazyVerticalGrid(GridCells.Fixed(2), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cards, key = { it.key }) { card ->
                        key(card.version) {
                            AndroidView(factory = { card.create.get() }, onRelease = { releaseLibraryImages(it) },
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (moreLabel.isNotEmpty()) TextButton(onClick = { more?.run() }, enabled = actionEnabled,
                    modifier = Modifier.fillMaxWidth()) { Text(moreLabel) }
            }
        }
    }
}

internal fun releaseLibraryImages(view: View) {
    if (view is ImageView) Glide.with(view).clear(view)
    if (view is ViewGroup) for (index in 0 until view.childCount) releaseLibraryImages(view.getChildAt(index))
}
