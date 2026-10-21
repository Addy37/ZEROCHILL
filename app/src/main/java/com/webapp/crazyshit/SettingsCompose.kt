package com.webapp.crazyshit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webapp.crazyshit.ui.*
import java.util.function.Consumer

/** Java controller owns actions and persistence; this boundary only renders snapshots. */
internal class SettingsCompose(private val activity: ComponentActivity) {
    class Group(val title: String) {
        private val rows = mutableListOf<Setting>()
        fun action(title: String, subtitle: String, value: String, action: Runnable) {
            rows += Setting(title, subtitle, value, action = action)
        }
        fun toggle(title: String, subtitle: String, checked: Boolean, action: Consumer<Boolean>) {
            rows += Setting(title, subtitle, checked = checked, toggle = action)
        }
        internal fun snapshot() = Section(title, rows.toList())
    }

    internal data class Setting(val title: String, val subtitle: String, val value: String = "",
        val checked: Boolean? = null, val action: Runnable? = null, val toggle: Consumer<Boolean>? = null)
    internal data class Section(val title: String, val rows: List<Setting>)

    private val dialogs = SettingsDialogs()
    fun dialog() = dialogs.builder()
    fun dialogTitle() = dialogs.title()

    private var sections by mutableStateOf(emptyList<Section>())
    var notificationStatus by mutableStateOf(NotificationCoordinator.statusSummary(activity))
    var sourceStatus by mutableStateOf("")

    init { activity.installZeroChillContent { Screen(); dialogs.Render() } }

    fun show(groups: List<Group>) { sections = groups.map { it.snapshot() } }

    @Composable private fun Screen() {
        ZeroChillScreen("Settings", activity::finish) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                items(sections, key = { it.title }) { section ->
                    ZeroChillSectionHeader(section.title)
                    ZeroChillPanel {
                        section.rows.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant)
                            if (row.checked != null) ZeroChillToggleRow(row.title, row.subtitle, row.checked,
                                onCheckedChange = { row.toggle?.accept(it) })
                            else ZeroChillActionRow(row.title, when (row.title) {
                                "Check creator updates" -> notificationStatus
                                "Source configuration" -> sourceStatus.ifEmpty { row.subtitle }
                                else -> row.subtitle
                            }, row.value) { row.action?.run() }
                        }
                    }
                }
                item(key = "version") {
                    Text("ZeroChill  •  ${BuildConfig.VERSION_NAME}\nCommunity Android client",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                }
            }
        }
    }
}

/** AndroidX owns lifecycle and state restoration, including lazy-list scroll state. */
internal fun ComponentActivity.installZeroChillContent(content: @Composable () -> Unit) {
    setContentView(ComposeView(this).apply {
        id = R.id.zerochill_compose_root
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { ZeroChillTheme(content) }
    })
}
