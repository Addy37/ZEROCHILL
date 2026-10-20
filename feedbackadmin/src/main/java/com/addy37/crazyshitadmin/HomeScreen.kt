package com.addy37.crazyshitadmin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable internal fun HomeScreen(state: ConsoleState) {
    val a = state.analytics
    val draft = state.config
    val sources = remember(draft?.text) { draft?.let { org.json.JSONObject(it.text).optJSONObject("sources") } }
    val enabled = sources?.keysList()?.count { sources.optJSONObject(it)?.optBoolean("enabled", true) == true }
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(22.dp), verticalArrangement=Arrangement.spacedBy(22.dp)) {
        item { Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Eyebrow("01 / OVERVIEW", color=Cyan)
            Text("Command center", style=MaterialTheme.typography.headlineLarge)
            Text("Your app, at a glance.", color=Muted)
        } }
        item {
            Box(Modifier.fillMaxWidth()) {
                ConsolePanel(Modifier.fillMaxWidth(), accent=true) {
                    Eyebrow("ACTIVE INSTALLS")
                    Metric(a?.dailyUsers, "Active today", large=true)
                    HorizontalDivider(color=Cyan.copy(alpha=.12f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                        Metric(a?.weeklyUsers, "This week", modifier=Modifier.weight(1f))
                        Metric(a?.monthlyUsers, "This month", modifier=Modifier.weight(1f))
                    }
                }
                EnergyField(Modifier.matchParentSize())
            }
            Spacer(Modifier.height(8.dp))
            Text(a?.generatedAt?.let { "Analytics · ${dateLabel(it)}" } ?: "Waiting for analytics", color=Muted, style=MaterialTheme.typography.bodySmall)
            ErrorPanel(state.errors["analytics"], state::loadAnalytics)
        }
        item {
            BoxWithConstraints {
                val wide = maxWidth > 580.dp
                val panels: @Composable (Modifier) -> Unit = { mod ->
                    ConsolePanel(mod) {
                        Eyebrow("FEEDBACK")
                        Text(state.items.count { it.unreadCount > 0 }.toString(), style=MaterialTheme.typography.headlineLarge)
                        Text("Unread threads · ${state.items.count { it.status == "submitted" }} new", color=Muted)
                        TextButton(onClick={state.navigate("feedback")}) { Text("Open inbox"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp)) }
                    }
                }
                val service: @Composable (Modifier) -> Unit = { mod ->
                    ConsolePanel(mod) {
                        Eyebrow("SOURCE CONTROL")
                        Text(if(sources == null) "Awaiting config" else "$enabled / ${sources.length()}", style=MaterialTheme.typography.headlineLarge)
                        Text("Sources enabled · runtime health unavailable", color=Muted)
                        TextButton(onClick={state.navigate("sources")}) { Text("Manage sources"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp)) }
                    }
                }
                if(wide) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) { panels(Modifier.weight(1f)); service(Modifier.weight(1f)) }
                else Column(verticalArrangement=Arrangement.spacedBy(14.dp)) { panels(Modifier.fillMaxWidth()); service(Modifier.fillMaxWidth()) }
            }
            Spacer(Modifier.height(12.dp))
            ErrorPanel(state.errors["feedback"], state::loadFeedback)
            ErrorPanel(state.errors["sources"], {state.loadConfig()})
        }
        item { Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Eyebrow("OPERATIONS")
            SnapshotRow("Remote configuration", draft?.let { if(it.published > 0) "Published v${it.published}" else "Bundled defaults" } ?: "Not loaded", Icons.Outlined.Tune) {state.navigate("sources")}
            if(draft?.dirty == true) StatusTag("Candidate has unpublished changes")
            SnapshotRow("Top creator", a?.creators?.firstOrNull()?.value ?: "No activity yet", Icons.Outlined.LocalFireDepartment) {state.navigate("analytics")}
            SnapshotRow("Most-used section", a?.sections?.firstOrNull()?.value?.let(::friendly) ?: "No activity yet", Icons.Outlined.Insights) {state.navigate("analytics")}
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Eyebrow("RECENT FEEDBACK", Modifier.weight(1f))
            TextButton(onClick={state.navigate("feedback")}) {Text("View all")}
        } }
        if("feedback" in state.busy && state.items.isEmpty()) item {InitialLoad("Loading inbox")}
        else if(state.items.isEmpty()) item {EmptyPanel("Inbox is clear", "New reports and replies will appear here.")}
        items(state.items.take(3), key={it.id}) { item -> FeedbackRow(item) {state.openThread(item.id)} }
        item { Text("Inbox refreshed ${dateLabel(state.refreshed).ifEmpty { "when connected" }}\nFeedback snapshot covers up to 200 recent tickets.", color=Muted, style=MaterialTheme.typography.bodySmall) }
    }
}
@Composable private fun SnapshotRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=click).padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint=Cyan)
        Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(label, color=Muted, style=MaterialTheme.typography.bodySmall)
            Text(value, fontWeight=FontWeight.Medium)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint=Muted)
    }
}
