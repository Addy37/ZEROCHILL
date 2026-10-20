package com.addy37.crazyshitadmin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun InboxScreen(state: ConsoleState) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var unread by rememberSaveable { mutableStateOf(false) }
    var oldest by rememberSaveable { mutableStateOf(false) }
    val shown = remember(state.items, query, type, status, unread, oldest) {filterFeedback(state.items,query,type,status,unread,oldest)}
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(22.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Eyebrow("02 / USER VOICE",color=Cyan); Spacer(Modifier.height(8.dp)); Text("Feedback",style=MaterialTheme.typography.headlineLarge) }
        item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Search messages, devices, versions")},singleLine=true,
            leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(query.isNotEmpty()) ActionIcon(Icons.Outlined.Close,"Clear search"){query=""}},shape=ConsoleShape) }
        item { Column {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(unread,{unread=!unread},label={Text("Unread")},leadingIcon={Icon(Icons.Outlined.MarkEmailUnread,null,Modifier.size(17.dp))})
                listOf("" to "All types","bug_report" to "Bugs","feature_request" to "Requests","general_feedback" to "General").forEach {(key,label)->
                    FilterChip(type==key,{type=key},label={Text(label)})
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (listOf("" to "All statuses") + statuses.toList()).forEach {(key,label)->FilterChip(status==key,{status=key},label={Text(label)})}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${shown.size} threads",Modifier.weight(1f),color=Muted)
                TextButton(onClick={oldest=!oldest}) {Icon(Icons.Outlined.Sort,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(if(oldest) "Oldest first" else "Newest first")}
            }
        } }
        item { ErrorPanel(state.errors["feedback"],state::loadFeedback) }
        if(state.items.isEmpty() && "feedback" in state.busy) item {InitialLoad("Loading feedback")}
        else if(shown.isEmpty()) item {EmptyPanel(if(query.isNotBlank() || type.isNotEmpty() || status.isNotEmpty() || unread) "No matching threads" else "Nothing waiting",
            if(state.errors["feedback"] != null) "Retry to load your inbox." else "New reports and replies will appear here. Try adjusting your filters.")}
        items(shown,key={it.id}) { item -> FeedbackRow(item) {state.openThread(item.id)} }
        item {Text("Search and filters cover the latest 200 tickets returned by the server.",color=Muted,style=MaterialTheme.typography.bodySmall)}
    }
}
@Composable internal fun FeedbackRow(item: AdminRepository.Item, click: () -> Unit) {
    val color = when(item.type) {"bug_report" -> Danger; "feature_request" -> Cyan; else -> Good}
    val icon = when(item.type) {"bug_report" -> Icons.Outlined.BugReport; "feature_request" -> Icons.Outlined.AutoAwesome; else -> Icons.Outlined.ChatBubbleOutline}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick=click).padding(17.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Icon(icon,null,tint=color,modifier=Modifier.size(19.dp))
            Text(typeLabel(item.type),Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=13.sp)
            if(item.unreadCount>0) StatusTag("${item.unreadCount} unread")
        }
        Text((if(item.lastSender=="developer") "You: " else "")+item.lastMessage, maxLines=2,overflow=TextOverflow.Ellipsis,
            style=MaterialTheme.typography.bodyLarge,fontWeight=if(item.unreadCount>0) FontWeight.SemiBold else FontWeight.Normal)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(statusLabel(item.status),color=if(item.status=="submitted") Cyan else Muted,fontSize=12.sp)
            Text("· ${dateLabel(item.lastMessageAt)}",color=Muted,fontSize=12.sp)
        }
        Text("v${item.appVersion} · ${item.device} · ${friendly(item.section)}" + if(item.rating>0) " · ${item.rating}/5 ★" else "",
            color=Muted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
