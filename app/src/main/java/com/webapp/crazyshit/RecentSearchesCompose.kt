package com.webapp.crazyshit

import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webapp.crazyshit.ui.ZeroChillTheme
import java.util.function.Consumer

internal class RecentSearchesCompose(activity: Activity, private val open: Consumer<String>,
    private val remove: Consumer<String>, private val clear: Runnable) {
    private val owner = NavOwner(activity)
    private var queries by mutableStateOf(emptyList<String>())
    fun repeatQuery(value: String) = open.accept(value)
    fun removeQuery(value: String) = remove.accept(value)
    fun clearQueries() = clear.run()
    fun show(value: List<String>) { queries = value.toList() }
    val view = ComposeView(activity).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ZeroChillTheme {
                LazyColumn {
                    item(key = "heading") {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent searches", fontSize = 16.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = clear::run, modifier = Modifier.semantics {
                                contentDescription = "Clear all recent searches"
                            }) { Text("Clear all") }
                        }
                    }
                    if (queries.isEmpty()) item(key = "empty") {
                        Text("Search creators or videos", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp))
                    }
                    items(queries, key = { it }) { query ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(query, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                    .clickable { open.accept(query) }.padding(horizontal = 14.dp, vertical = 16.dp)
                                    .semantics { contentDescription = "Search again for $query" })
                            TextButton(onClick = { remove.accept(query) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { contentDescription = "Remove recent search $query" }) { Text("×") }
                        }
                    }
                }
            }
        }
    }
    fun close() { view.disposeComposition(); owner.close() }
}
