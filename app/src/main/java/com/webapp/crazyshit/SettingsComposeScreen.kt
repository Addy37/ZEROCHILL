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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
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

    @JvmStatic
    fun mount(activity: SettingsActivity, sections: List<Section>) {
        val view = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { SettingsPage(activity, sections) }
        }
        activity.setContentView(view)
    }

    @Composable
    private fun SettingsPage(activity: SettingsActivity, sections: List<Section>) {
        val accent by animateColorAsState(
            Color(AppearanceTheme.accent(activity)), tween(220), label = "settings accent"
        )
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = accent, onPrimary = Black, background = Black,
                onBackground = Ink, surface = Surface, onSurface = Ink,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(Black)
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                            .clickable { activity.finish() }
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) { Text("‹", fontSize = 34.sp, color = accent) }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Settings", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("ZEROCHILL  /  PREFERENCES", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }

                sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 5.dp, top = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(6.dp).background(accent, CircleShape))
                            Text(
                                section.title.uppercase(),
                                color = Muted, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp, letterSpacing = .8.sp,
                            )
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            border = BorderStroke(1.dp, Stroke),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                section.items.forEachIndexed { index, item ->
                                    if (index > 0) HorizontalDivider(
                                        color = Stroke.copy(alpha = .8f),
                                        thickness = .6.dp,
                                        modifier = Modifier.padding(horizontal = 13.dp),
                                    )
                                    SettingRow(item, accent)
                                }
                            }
                        }
                    }
                }

                Text(
                    "ZeroChill  •  " + BuildConfig.VERSION_NAME + "\nCommunity Android client",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
            }
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
                    color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
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
