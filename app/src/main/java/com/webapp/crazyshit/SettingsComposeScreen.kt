package com.webapp.crazyshit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.function.Consumer

/**
 * Compose visual shell for Settings. The existing Java SettingsActivity owns
 * every action, account flow, notification preference and backup callback.
 */
object SettingsComposeScreen {
    private val Black = Color(0xFF000000)
    private val Surface = Color(0xFF101216)
    private val Raised = Color(0xFF1B2027)
    private val Stroke = Color(0xFF303841)
    private val Ink = Color(0xFFF5F7FA)
    private val Secondary = Color(0xFFB8C0CC)
    private val Muted = Color(0xFF929DAC)

    /** Java-facing model, intentionally independent of Android Views. */
    class Section(val title: String) {
        val items = mutableStateListOf<Item>()

        fun addAction(title: String, subtitle: String, trailing: String, action: Runnable): Item {
            val row = Item(title, subtitle, trailing, null, action, null)
            items.add(row)
            return row
        }

        fun addSwitch(title: String, subtitle: String, checked: Boolean, onChange: Consumer<Boolean>): Item {
            val row = Item(title, subtitle, "", checked, null, onChange)
            items.add(row)
            return row
        }
    }

    class Item(
        val title: String,
        subtitle: String,
        val trailing: String,
        initialChecked: Boolean?,
        val action: Runnable?,
        val onChange: Consumer<Boolean>?,
    ) {
        var subtitle by mutableStateOf(subtitle)
        var checked by mutableStateOf(initialChecked)

        fun toggle(value: Boolean) {
            if (checked == value) return
            checked = value
            onChange?.accept(value)
        }
    }

    class Navigation(initialPage: String) {
        var page by mutableStateOf(initialPage)
            private set
        var query by mutableStateOf("")
        var target by mutableStateOf("")
            private set

        fun open(section: String, item: String = "") {
            page = section
            target = item
            query = ""
        }

        fun back(): Boolean {
            if (page.isNotEmpty()) { open(""); return true }
            if (query.isNotEmpty()) { query = ""; return true }
            return false
        }
    }

    private data class Destination(val title: String, val summary: String, val icon: Int)
    private val destinations = listOf(
        Destination("Appearance", "Themes, colors, glass and motion", R.drawable.ic_more_view_style),
        Destination("Notifications", "Messages, creators and app alerts", R.drawable.ic_more_update),
        Destination("Content & playback", "Preferences, preloading and blocked creators", R.drawable.ic_player_play),
        Destination("Account & privacy", "Profile, security and privacy", R.drawable.ic_more_account),
        Destination("Library & storage", "History, saved items and backups", R.drawable.ic_more_library),
        Destination("App updates", "Check for updates and What's New", R.drawable.ic_action_replay),
        Destination("Advanced", "Source configuration and diagnostics", R.drawable.ic_more_settings),
        Destination("About ZEROCHILL", "Version and startup tour", R.drawable.ic_more_help),
    )

    @JvmStatic
    fun mount(activity: SettingsActivity, sections: List<Section>, navigation: Navigation) {
        val view = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { SettingsPage(activity, sections, navigation) }
        }
        activity.setContentView(view)
    }

    @Composable
    private fun SettingsPage(activity: SettingsActivity, sections: List<Section>, navigation: Navigation) {
        val accent by animateColorAsState(
            Color(AppearanceTheme.accent(activity)), tween(220), label = "settings accent"
        )
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = accent, onPrimary = Black, background = Black,
                onBackground = Ink, surface = Surface, onSurface = Ink,
            ),
        ) {
            val current = sections.firstOrNull { it.title == navigation.page }
            Column(
                modifier = Modifier.fillMaxSize().background(Black)
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                            .clickable { activity.navigateBack() }
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_player_back), null,
                            tint = Ink, modifier = Modifier.size(24.dp))
                    }
                    Text(current?.title ?: "Settings", color = Ink,
                        fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                key(current?.title, navigation.target) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    ) {
                        if (current == null) {
                            OutlinedTextField(
                                value = navigation.query,
                                onValueChange = { navigation.query = it },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                placeholder = { Text("Search settings", color = Muted) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_nav_search), null,
                                    tint = Muted, modifier = Modifier.size(20.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                            )
                            val query = navigation.query.trim()
                            if (query.isEmpty()) {
                                destinations.filter { d -> sections.any { it.title == d.title } }
                                    .forEach { destination ->
                                        HubRow(destination.title, destination.summary, destination.icon, accent) {
                                            navigation.open(destination.title)
                                        }
                                        SettingsDivider()
                                    }
                                Column(Modifier.fillMaxWidth().padding(top = 22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row {
                                        Text("ZERO", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Text("CHILL", color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    Text(BuildConfig.VERSION_NAME, color = Muted, fontSize = 11.sp)
                                }
                            } else {
                                val matches = sections.flatMap { section ->
                                    section.items.filter { matchesSetting(section.title, it, query) }
                                        .map { section.title to it }
                                }
                                if (matches.isEmpty()) Text("No matching settings", color = Muted,
                                    modifier = Modifier.padding(16.dp))
                                matches.forEach { (section, item) ->
                                    HubRow(item.title, section, R.drawable.ic_more_settings, accent) {
                                        navigation.open(section, item.title)
                                    }
                                    SettingsDivider()
                                }
                            }
                        } else {
                            if (current.title == "Notifications") Text(
                                "Device alerts and account-synced preferences are separate controls.",
                                color = Secondary, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                            val ordered = if (navigation.target.isEmpty()) current.items.toList()
                                else current.items.sortedBy { it.title != navigation.target }
                            ordered.forEach { item ->
                                SettingRow(item, accent)
                                SettingsDivider()
                            }
                            if (current.title == "About ZEROCHILL") Text(
                                "ZEROCHILL " + BuildConfig.VERSION_NAME + "\nNO LIMITS. ALL CONTENT.",
                                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun matchesSetting(section: String, item: Item, query: String): Boolean {
        val haystack = listOf(section, item.title, item.subtitle, item.trailing).joinToString(" ")
        return query.trim().split(Regex("\\s+")).all { haystack.contains(it, ignoreCase = true) }
    }

    @Composable
    private fun SettingsDivider() {
        HorizontalDivider(color = Stroke.copy(alpha = .55f), thickness = .6.dp,
            modifier = Modifier.padding(horizontal = 12.dp))
    }

    @Composable
    private fun HubRow(title: String, summary: String, icon: Int, accent: Color, action: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = action)
                .padding(horizontal = 8.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .10f)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(summary, color = Secondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text("›", color = Muted, fontSize = 23.sp)
        }
    }

    @Composable
    private fun SettingRow(item: Item, accent: Color) {
        val isToggle = item.onChange != null
        val clickModifier = if (isToggle) {
            Modifier.clickable { item.toggle(item.checked != true) }
        } else {
            Modifier.clickable { item.action?.run() }
        }

        Row(
            modifier = Modifier.fillMaxWidth().then(clickModifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    item.title,
                    color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        color = Secondary, fontSize = 12.sp,
                        lineHeight = 16.sp, maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isToggle) {
                Switch(
                    checked = item.checked == true,
                    onCheckedChange = item::toggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Black, checkedTrackColor = accent,
                        uncheckedTrackColor = Raised, uncheckedThumbColor = Secondary,
                    ),
                )
            } else {
                if (item.trailing.isNotBlank()) {
                    Text(
                        item.trailing,
                        color = accent,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(98.dp),
                        textAlign = TextAlign.End,
                    )
                }
                Text("›", fontSize = 23.sp, color = Muted)
            }
        }
    }
}
