package com.webapp.crazyshit

import android.content.Context
import android.view.Menu
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.webapp.crazyshit.ui.ZeroChillTheme
import java.util.function.Supplier

/** RecyclerView retains bounded prefetch and its existing saved scroll contract. */
internal class CreatorCardCompose(context: Context, private val avatar: View) {
    private class Snapshot(val key: String, val name: String, val unread: Int, val profiles: Int,
        val open: Runnable, val members: Runnable, val actions: Supplier<ZeroChillMenu>)
    private var snapshot by mutableStateOf<Snapshot?>(null)
    fun bind(key: String, name: String, unread: Int, profiles: Int, open: Runnable,
        members: Runnable, actions: Supplier<ZeroChillMenu>) {
        snapshot = Snapshot(key, name, unread, profiles, open, members, actions)
    }
    fun clear() { snapshot = null }
    val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setContent {
            ZeroChillTheme {
                snapshot?.let { card ->
                    var actions by remember(card.key) { mutableStateOf<ZeroChillMenu?>(null) }
                    var submenu by remember(card.key) { mutableStateOf<Menu?>(null) }
                    fun openOptions() { actions = card.actions.get(); submenu = null }
                    Box(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .combinedClickable(onClick = card.open::run, onLongClick = { openOptions() },
                                onLongClickLabel = "Creator options")
                            .semantics {
                                contentDescription = "Open ${card.name}" + if (card.unread > 0)
                                    ". ${card.unread} new items." else ""
                                customActions = listOf(CustomAccessibilityAction("Creator options") {
                                    openOptions(); true
                                })
                            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(CircleShape)) {
                                AndroidView(factory = { avatar }, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(card.name, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (card.profiles > 1) TextButton(onClick = card.members::run,
                                    contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)
                                        .semantics { contentDescription = "Merged creator, ${card.profiles} profiles. Tap to view merged creators." }) {
                                    Text("${card.profiles} profiles", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (card.unread > 0) Text(if (card.unread > 9) "9+" else "${card.unread}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 8.dp).background(
                                    MaterialTheme.colorScheme.primary, CircleShape).padding(7.dp)
                                    .semantics { contentDescription = "${card.unread} new items from ${card.name}" })
                        }
                        DropdownMenu(expanded = actions != null, onDismissRequest = { actions = null; submenu = null }) {
                            val menu = submenu ?: actions?.getMenu()
                            if (submenu != null) DropdownMenuItem(text = { Text("Back") }, onClick = { submenu = null })
                            if (menu != null) for (index in 0 until menu.size()) {
                                val item = menu.getItem(index)
                                DropdownMenuItem(text = { Text(item.title.toString()) }, onClick = {
                                    if (item.hasSubMenu()) submenu = item.subMenu
                                    else { val source = actions; actions = null; submenu = null; source?.dispatch(item) }
                                }, enabled = item.isEnabled)
                            }
                        }
                    }
                }
            }
        }
    }
}
