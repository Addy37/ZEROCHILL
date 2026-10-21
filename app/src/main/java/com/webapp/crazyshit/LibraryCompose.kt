package com.webapp.crazyshit

import android.view.View
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.webapp.crazyshit.ui.*
import java.util.function.Consumer
import java.util.function.Supplier

/** Collection controllers publish presentation snapshots without changing their storage contracts. */
internal class LibraryCompose(private val activity: ComponentActivity, private val title: String,
    private val onQuery: Consumer<String>) {
    class Card(val key: String, val title: String, val description: String, val detail: String,
        val pill: String, val progress: Float, val createImage: Supplier<ImageView>,
        val releaseImage: Consumer<ImageView>, val open: Runnable, val removeLabel: String, val remove: Runnable)
    var searchable by mutableStateOf(true)
    var query by mutableStateOf("")
    var summary by mutableStateOf("")
    var emptyMessage by mutableStateOf("")
    var actionLabel by mutableStateOf("")
    var actionEnabled by mutableStateOf(true)
    var action: Runnable? = null
    var sectionActionLabel by mutableStateOf("")
    private class Confirmation(val title: String, val message: String, val accept: Runnable)
    private var confirmation by mutableStateOf<Confirmation?>(null)
    fun confirm(title: String, message: String, accept: Runnable) { confirmation = Confirmation(title, message, accept) }
    var moreLabel by mutableStateOf("")
    var more: Runnable? = null
    private var cards by mutableStateOf(emptyList<Card>())
    fun showCards(value: List<Card>) { cards = value.toList() }
    fun install(content: View?) {
        activity.installZeroChillContent {
            ZeroChillScreen(title, activity::finish, actions = {
                if (actionLabel == "Section options") {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)
                            .semantics { contentDescription = "Section options" }) { Text("⋮", fontSize = 24.sp) }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text(sectionActionLabel) }, onClick = {
                                expanded = false; action?.run()
                            })
                        }
                    }
                } else if (actionLabel == "Find creators") {
                    IconButton(onClick = { action?.run() }, enabled = actionEnabled,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = actionLabel }) {
                        Text("+", fontSize = 24.sp)
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
                        SavedMediaCard(card)
                    }
                }
                if (moreLabel.isNotEmpty()) TextButton(onClick = { more?.run() }, enabled = actionEnabled,
                    modifier = Modifier.fillMaxWidth()) { Text(moreLabel) }
            }
            confirmation?.let { request ->
                AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(request.title) },
                    text = { Text(request.message) },
                    dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
                    confirmButton = { TextButton(onClick = {
                        confirmation = null; request.accept.run()
                    }) { Text("Clear", color = MaterialTheme.colorScheme.error) } })
            }
        }
    }
}

/** Only image delivery remains a View so resolver/cache/fade behavior stays unchanged. */
@Composable
private fun SavedMediaCard(card: LibraryCompose.Card) {
    var expanded by rememberSaveable(card.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).clickable(onClick = card.open::run)
            .semantics { contentDescription = card.description }) {
            AndroidView(factory = { card.createImage.get() }, onRelease = { card.releaseImage.accept(it) },
                modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.Transparent, Color.Black.copy(alpha = .13f), Color.Black.copy(alpha = .88f)))))
            Column(Modifier.fillMaxWidth().heightIn(min = 102.dp).padding(bottom = 11.dp),
                verticalArrangement = Arrangement.Bottom) {
                val playArea = if (card.pill.isEmpty()) 48.dp else 64.dp + with(LocalDensity.current) { 12.sp.toDp() }
                Box(Modifier.fillMaxWidth().height(playArea), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.size(38.dp).background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_player_play), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Text(card.title, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp, end = 48.dp))
            }
            if (card.pill.isNotEmpty()) Text(card.pill, color = Color.White, fontSize = 9.sp, lineHeight = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp))
            if (card.progress >= 0) LinearProgressIndicator(progress = { card.progress.coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                    .height(3.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.White.copy(alpha = .43f),
                drawStopIndicator = {})
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = "Options for ${card.title}" }) {
                    Text("⋮", fontSize = 22.sp, color = Color.White,
                        modifier = Modifier.background(Color.Black.copy(alpha = .6f), RoundedCornerShape(17.dp))
                            .padding(horizontal = 10.dp))
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(card.removeLabel) }, onClick = {
                        expanded = false; card.remove.run()
                    })
                }
            }
        }
        Text(card.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 3.dp, end = 3.dp, top = 6.dp, bottom = 2.dp))
    }
}
