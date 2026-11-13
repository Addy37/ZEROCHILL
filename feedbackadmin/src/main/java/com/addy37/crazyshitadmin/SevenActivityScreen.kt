package com.addy37.crazyshitadmin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The 7.0 backend intentionally does not collect legacy device analytics.
 * Only the authenticated private admin's aggregate social counts are shown.
 */
@Composable internal fun SevenActivityScreen(state: ConsoleState) {
    val snapshot = state.communityOverview
    LaunchedEffect(Unit) {
        if (snapshot == null) state.loadCommunityOverview()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Eyebrow("04 / LIVE ACTIVITY", color = Cyan)
            Spacer(Modifier.height(8.dp))
            Text("Community activity", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Current New Zerochill accounts and social totals.", color = Muted)
        }
        item {
            ErrorPanel(state.errors["communityOverview"], state::loadCommunityOverview)
        }
        if (snapshot == null) {
            item {
                if ("communityOverview" in state.busy) InitialLoad("Loading community totals")
                else EmptyPanel("Activity unavailable", "Refresh to try again.", Icons.Outlined.Insights)
            }
        } else {
            item {
                ConsolePanel(Modifier.fillMaxWidth(), accent = true) {
                    Eyebrow("ACCOUNT ACTIVITY")
                    Metric(snapshot.accounts, "Registered accounts", large = true)
                    HorizontalDivider(color = Cyan.copy(alpha = .12f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Metric(snapshot.newAccounts7d, "New in 7 days", modifier = Modifier.weight(1f))
                        Metric(snapshot.reports, "Reports", modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                ConsolePanel(Modifier.fillMaxWidth()) {
                    Eyebrow("COMMUNITY")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Metric(snapshot.comments, "Comments", modifier = Modifier.weight(1f))
                        Metric(snapshot.messages, "DMs sent", modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = Cyan.copy(alpha = .12f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Metric(snapshot.videoLikes, "Video likes", modifier = Modifier.weight(1f))
                        Metric(snapshot.commentLikes, "Comment likes", modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = Cyan.copy(alpha = .12f))
                    Metric(snapshot.blocks, "User blocks")
                }
            }
            item {
                Text(
                    "These are database totals, not usage tracking. ZEROCHILL 7.0 does not send the old analytics events. No message contents are shown.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
