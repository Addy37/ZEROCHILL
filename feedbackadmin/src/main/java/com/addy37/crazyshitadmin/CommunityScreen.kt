package com.addy37.crazyshitadmin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun CommunityScreen(state: ConsoleState) {
    var search by rememberSaveable { mutableStateOf(state.communityQuery) }
    val focus = LocalFocusManager.current
    val overview = state.communityOverview
    val filters = when(state.communitySection) {
        "social" -> listOf("all" to "All", "comments" to "Comments", "likes" to "Likes", "messages" to "Messages")
        "reports" -> listOf("all" to "All", "spam" to "Spam", "harassment" to "Harassment", "other" to "Other")
        else -> listOf("all" to "All", "active" to "Active", "unconfirmed" to "Unconfirmed", "banned" to "Banned")
    }

    LaunchedEffect(Unit) {
        if(state.communityOverview == null) state.loadCommunityOverview()
        state.loadCommunityList()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(22.dp), verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Eyebrow("03 / COMMUNITY", color=Cyan)
                        Text("Community", style=MaterialTheme.typography.headlineLarge)
                        Text("Accounts, social activity, and reports.", color=Muted)
                    }
                    StatusTag("READ ONLY", Good)
                }
                Text("No moderation actions in this first pass. Direct-message bodies stay private.", color=Muted, fontSize=12.sp)
            }
        }
        item {
            ConsolePanel(Modifier.fillMaxWidth(), accent=true) {
                Eyebrow("COMMUNITY SNAPSHOT")
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    Metric(overview?.accounts, "Accounts", modifier=Modifier.weight(1f))
                    Metric(overview?.newAccounts7d, "New · 7 days", modifier=Modifier.weight(1f))
                }
                HorizontalDivider(color=Cyan.copy(alpha=.12f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    val activity = overview?.let { it.comments + it.totalLikes() + it.messages }
                    Metric(activity, "Social activity", modifier=Modifier.weight(1f))
                    Metric(overview?.reports, "Reports", modifier=Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SectionChip("Accounts", "accounts", state) { search="" }
                SectionChip("Social", "social", state) { search="" }
                SectionChip("Reports", "reports", state) { search="" }
            }
        }
        item {
            OutlinedTextField(
                value=search,
                onValueChange={search=it},
                modifier=Modifier.fillMaxWidth(),
                singleLine=true,
                shape=ConsoleShape,
                label={Text(searchLabel(state.communitySection))},
                leadingIcon={Icon(Icons.Outlined.Search, null)},
                trailingIcon={
                    if(search.isNotEmpty()) ActionIcon(Icons.Outlined.Close, "Clear search") {
                        search=""; state.searchCommunity(""); focus.clearFocus()
                    }
                },
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(onSearch={state.searchCommunity(search);focus.clearFocus()})
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                filters.forEach { (value,label) ->
                    val selected = state.communityFilter == value
                    FilterChip(selected=selected, onClick={state.setCommunityFilter(value)}, label={Text(label)},
                        leadingIcon=if(selected) {{Icon(Icons.Outlined.Check,null,Modifier.size(16.dp))}} else null)
                }
            }
        }
        item {
            ErrorPanel(state.errors["communityOverview"], state::loadCommunityOverview)
            ErrorPanel(state.errors["communityList"], state::loadCommunityList)
            ErrorPanel(state.errors["accountDetail"])
        }
        when(state.communitySection) {
            "social" -> {
                if("communityList" in state.busy && state.communityActivity.isEmpty()) item { InitialLoad("Loading social activity") }
                else if(state.communityActivity.isEmpty()) item { EmptyPanel("No social activity", "Nothing matches this search or filter.", Icons.Outlined.Forum) }
                else items(state.communityActivity, key={it.type+":"+it.id}) { ActivityRow(it) }
            }
            "reports" -> {
                if("communityList" in state.busy && state.communityReports.isEmpty()) item { InitialLoad("Loading reports") }
                else if(state.communityReports.isEmpty()) item { EmptyPanel("No reports", "Reported accounts or messages will appear here.", Icons.Outlined.ReportProblem) }
                else items(state.communityReports, key={it.id}) { ReportRow(it) }
            }
            else -> {
                if("communityList" in state.busy && state.communityAccounts.isEmpty()) item { InitialLoad("Loading accounts") }
                else if(state.communityAccounts.isEmpty()) item { EmptyPanel("No accounts found", "Try a different search or account filter.", Icons.Outlined.ManageAccounts) }
                else items(state.communityAccounts, key={it.id}) { account -> AccountRow(account) { state.loadAccountDetail(account.id) } }
            }
        }
        item {
            val count = when(state.communitySection) {
                "social" -> state.communityActivity.size
                "reports" -> state.communityReports.size
                else -> state.communityAccounts.size
            }
            Text("Showing ${number(count.toLong())} recent result${if(count==1) "" else "s"}. Lists are capped at 100 items per request.", color=Muted, fontSize=12.sp)
        }
    }

    state.accountDetail?.let { detail -> AccountDetailDialog(detail, state::closeAccountDetail) }
}

@Composable private fun SectionChip(label: String, value: String, state: ConsoleState, after: () -> Unit) {
    val selected = state.communitySection == value
    FilterChip(selected=selected, onClick={state.navigateCommunitySection(value);after()}, label={Text(label)},
        leadingIcon=if(selected) {{Icon(Icons.Outlined.Check,null,Modifier.size(16.dp))}} else null)
}

private fun searchLabel(section: String) = when(section) {
    "social" -> "Search people, comments, or content"
    "reports" -> "Search reporter, account, or reason"
    else -> "Search username, email, account ID, or bio"
}

@Composable private fun AccountRow(account: CommunityRepository.Account, open: () -> Unit) {
    val statusColor = when(account.status) { "active" -> Good; "banned" -> Danger; else -> Muted }
    ConsolePanel(Modifier.fillMaxWidth().clickable(onClick=open)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.Top, horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Cyan.copy(alpha=.12f)), contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.Person, null, tint=Cyan)
            }
            Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(account.name(), fontWeight=FontWeight.SemiBold, maxLines=1, overflow=TextOverflow.Ellipsis)
                val identity = listOf(account.handle(), account.email).filter {it.isNotBlank()}.joinToString(" · ")
                if(identity.isNotEmpty()) Text(identity, color=Muted, fontSize=12.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
                Text("Joined ${dateLabel(account.createdAt).ifEmpty { "Unknown" }}", color=Muted, fontSize=12.sp)
            }
            StatusTag(account.status.uppercase(), statusColor)
        }
    }
}

@Composable private fun ActivityRow(item: CommunityRepository.SocialActivity) {
    val (icon,label) = when(item.type) {
        "comment" -> Icons.Outlined.ChatBubbleOutline to if(item.state=="reply") "replied" else "commented"
        "video_like" -> Icons.Outlined.FavoriteBorder to "liked a video"
        "comment_like" -> Icons.Outlined.ThumbUpOffAlt to "liked a comment"
        "message" -> Icons.Outlined.MailOutline to "sent a message"
        else -> Icons.Outlined.Forum to friendly(item.type)
    }
    ConsolePanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint=Cyan)
            Column(Modifier.weight(1f)) {
                Text("${item.actor()} $label", fontWeight=FontWeight.SemiBold)
                Text(dateLabel(item.createdAt), color=Muted, fontSize=12.sp)
            }
            if(item.state.isNotEmpty()) StatusTag(friendly(item.state))
        }
        if(item.text.isNotBlank()) Text(item.text, maxLines=4, overflow=TextOverflow.Ellipsis)
        if(item.secondary.isNotBlank()) Text(item.secondary, color=Muted, fontSize=12.sp, maxLines=2, overflow=TextOverflow.Ellipsis)
    }
}

@Composable private fun ReportRow(item: CommunityRepository.Report) {
    ConsolePanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.ReportProblem, null, tint=Danger)
            Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text("${nameOrId(item.reporterName,item.reporterId)} reported ${nameOrId(item.reportedName,item.reportedUserId)}", fontWeight=FontWeight.SemiBold)
                Text(dateLabel(item.createdAt), color=Muted, fontSize=12.sp)
            }
            StatusTag(item.reason.uppercase(), Danger)
        }
        if(item.details.isNotBlank()) Text(item.details, maxLines=5, overflow=TextOverflow.Ellipsis)
        if(item.directMessageId.isNotBlank()) Text("Direct-message report · message body is not exposed", color=Muted, fontSize=12.sp)
    }
}

@Composable private fun AccountDetailDialog(detail: CommunityRepository.AccountDetail, dismiss: () -> Unit) {
    val account = detail.account
    AlertDialog(
        onDismissRequest=dismiss,
        icon={Icon(Icons.Outlined.ManageAccounts,null,tint=Cyan)},
        title={Column { Text(account.name()); if(account.handle().isNotEmpty()) Text(account.handle(),color=Muted,fontSize=13.sp) }},
        text={
            Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(14.dp)) {
                ReadOnlyBanner()
                DetailSection("ACCOUNT") {
                    DetailLine("Email", account.email.ifEmpty {"Not set"})
                    DetailLine("Status", account.status.uppercase())
                    DetailLine("User ID", account.id)
                    DetailLine("Joined", dateLabel(account.createdAt).ifEmpty {"Unknown"})
                    DetailLine("Last sign-in", dateLabel(account.lastSignInAt).ifEmpty {"Never"})
                    DetailLine("Email confirmed", dateLabel(account.emailConfirmedAt).ifEmpty {"No"})
                    if(account.bannedUntil.isNotBlank()) DetailLine("Banned until", dateLabel(account.bannedUntil))
                    if(account.bio.isNotBlank()) DetailLine("Bio", account.bio)
                    DetailLine("Avatar", if(account.avatarPath.isBlank()) "Not set" else "Set")
                }
                DetailSection("SOCIAL TOTALS") {
                    DetailLine("Comments", number(detail.comments))
                    DetailLine("Likes", "${number(detail.videoLikes)} videos · ${number(detail.commentLikes)} comments")
                    DetailLine("Messages", "${number(detail.messagesSent)} sent · ${number(detail.messagesReceived)} received")
                    DetailLine("Reports", "${number(detail.reportsFiled)} filed · ${number(detail.reportsReceived)} received")
                    DetailLine("Blocks", "${number(detail.blocksMade)} made · ${number(detail.blocksReceived)} received")
                }
                ActivityDetailSection("RECENT COMMENTS", detail.recentComments)
                ActivityDetailSection("RECENT LIKES", detail.recentLikes)
                ActivityDetailSection("RECENT MESSAGE METADATA", detail.recentMessages)
                ReportDetailSection("RECENT REPORTS", detail.recentReports)
            }
        },
        confirmButton={TextButton(onClick=dismiss){Text("Close")}}
    )
}

@Composable private fun ReadOnlyBanner() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Good.copy(alpha=.08f)).padding(12.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Visibility, null, tint=Good)
        Text("Read-only account view", color=Good, fontWeight=FontWeight.Medium)
    }
}

@Composable private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Eyebrow(title,color=Cyan); content() }
}

@Composable private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label, color=Muted, fontSize=11.sp)
        Text(value, fontSize=13.sp)
    }
}

@Composable private fun ActivityDetailSection(title: String, items: List<CommunityRepository.SocialActivity>) {
    if(items.isEmpty()) return
    DetailSection(title) {
        items.forEach { item ->
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(activityDescription(item), fontWeight=FontWeight.Medium, fontSize=13.sp)
                if(item.text.isNotBlank()) Text(item.text, fontSize=13.sp, maxLines=4, overflow=TextOverflow.Ellipsis)
                if(item.secondary.isNotBlank()) Text(item.secondary, color=Muted, fontSize=11.sp, maxLines=2, overflow=TextOverflow.Ellipsis)
                Text(dateLabel(item.createdAt), color=Muted, fontSize=11.sp)
            }
            HorizontalDivider(color=Muted.copy(alpha=.10f))
        }
    }
}

@Composable private fun ReportDetailSection(title: String, items: List<CommunityRepository.Report>) {
    if(items.isEmpty()) return
    DetailSection(title) {
        items.forEach { item ->
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text("${nameOrId(item.reporterName,item.reporterId)} → ${nameOrId(item.reportedName,item.reportedUserId)} · ${friendly(item.reason)}", fontWeight=FontWeight.Medium, fontSize=13.sp)
                if(item.details.isNotBlank()) Text(item.details, fontSize=13.sp)
                Text(dateLabel(item.createdAt), color=Muted, fontSize=11.sp)
            }
            HorizontalDivider(color=Muted.copy(alpha=.10f))
        }
    }
}

private fun activityDescription(item: CommunityRepository.SocialActivity) = when(item.type) {
    "comment" -> if(item.state=="reply") "Replied" else "Commented"
    "video_like" -> "Liked a video"
    "comment_like" -> "Liked a comment"
    "message" -> "Message metadata"
    else -> friendly(item.type)
}

private fun nameOrId(name: String, id: String) = name.ifBlank { id }
