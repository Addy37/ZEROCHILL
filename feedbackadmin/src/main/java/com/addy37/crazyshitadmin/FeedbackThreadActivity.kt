package com.addy37.crazyshitadmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class FeedbackThreadActivity : ComponentActivity() {
    private val state: ConsoleState by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        val id = intent.getStringExtra(EXTRA_FEEDBACK_ID)
        if(id.isNullOrBlank()) {finish();return}
        if(state.paired) state.openThread(id)
        setContent { ConsoleTheme { ConsoleApp(state, threadBack={finish()}) } }
    }
    companion object {const val EXTRA_FEEDBACK_ID = "feedback_id"}
}
@Composable internal fun ThreadScreen(state: ConsoleState, onBack: () -> Unit) {
    val thread = state.thread
    val id = state.threadId
    val item = thread?.item
    var draft by remember(id) {mutableStateOf(state.draft(id))}
    var details by rememberSaveable(id) {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)}
    var statusMenu by remember {mutableStateOf(false)}
    var delete by rememberSaveable(id) {mutableStateOf(false)}
    val actionBusy = "threadAction" in state.busy
    val list = rememberLazyListState()
    val snackbar = remember {SnackbarHostState()}
    BackHandler(onBack=onBack)
    LaunchedEffect(id) {if(thread == null) state.loadThread()}
    LaunchedEffect(state.notice) {
        if(state.notice.isNotEmpty()) {
            if(state.notice == "Reply sent") draft = state.draft(id)
            snackbar.showSnackbar(state.notice);state.consumeNotice()
        }
    }
    LaunchedEffect(thread?.messages?.size) { if(!thread?.messages.isNullOrEmpty()) list.animateScrollToItem(thread!!.messages.lastIndex) }
    Scaffold(containerColor=Ink, snackbarHost={SnackbarHost(snackbar)},
        topBar={Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                ActionIcon(Icons.AutoMirrored.Outlined.ArrowBack,"Back",onClick=onBack)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Eyebrow("FEEDBACK / CONVERSATION",color=Cyan)
                    Text(item?.let {typeLabel(it.type)} ?: "Conversation",style=MaterialTheme.typography.titleLarge)
                    if(item != null) Text("v${item.appVersion} · ${friendly(item.section)}",color=Muted,fontSize=12.sp)
                }
                Box {
                    ActionIcon(Icons.Outlined.MoreVert,"Thread actions") {menu=true}
                    DropdownMenu(menu,{menu=false}) {
                        DropdownMenuItem(text={Text("Refresh")},onClick={menu=false;state.loadThread()},leadingIcon={Icon(Icons.Outlined.Refresh,null)})
                        DropdownMenuItem(text={Text("Delete thread",color=Danger)},enabled=item!=null && !actionBusy,onClick={menu=false;delete=true},leadingIcon={Icon(Icons.Outlined.DeleteOutline,null,tint=Danger)})
                    }
                }
            }
            BusyLine("thread" in state.busy || actionBusy)
            Row(Modifier.fillMaxWidth().padding(horizontal=22.dp),verticalAlignment=Alignment.CenterVertically) {
                Box {
                    TextButton(onClick={statusMenu=true},enabled=item!=null && !actionBusy) {
                        Text(item?.let {statusLabel(state.threadStatus.ifEmpty {it.status})} ?: "Loading status");Icon(Icons.Outlined.ExpandMore,null)
                    }
                    DropdownMenu(statusMenu,{statusMenu=false}) {
                        statuses.forEach {(key,label)-> DropdownMenuItem(text={Text(label)},onClick={statusMenu=false;if(state.threadStatus!=key) state.updateStatus(key)},
                            trailingIcon={if(state.threadStatus==key) Icon(Icons.Outlined.Check,null,tint=Cyan)}) }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick={details=!details},enabled=item!=null) {Text(if(details) "Hide details" else "Details");Icon(if(details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)}
            }
            AnimatedVisibility(details && item!=null) {
                Column(Modifier.fillMaxWidth().heightIn(max=220.dp).verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(item!=null) {
                        Text("App ${item.appVersion} · Android ${item.androidVersion}",color=Muted)
                        Text("${item.device} · ${friendly(item.section)}",color=Muted)
                        Text("Submitted ${dateLabel(item.createdAt)}",color=Muted)
                        if(item.rating>0) Text("Rating ${item.rating} / 5",color=Muted)
                        SelectionContainer { Text("ID ${item.id}",color=Muted,fontSize=12.sp) }
                    }
                }
            }
        }},
        bottomBar={ Column(Modifier.background(Ink).navigationBarsPadding().imePadding().padding(horizontal=16.dp,vertical=10.dp)) {
            ErrorPanel(state.errors["threadAction"])
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft,{draft=it.take(2000);state.setDraft(id,draft)},Modifier.weight(1f),placeholder={Text("Reply to user…")},
                    enabled=item!=null && !actionBusy, maxLines=4,shape=RoundedCornerShape(24.dp))
                FilledIconButton(onClick={state.sendReply(draft)},enabled=item!=null && !actionBusy && draft.isNotBlank(),modifier=Modifier.size(54.dp)) {
                    if(actionBusy) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp,color=Ink)
                    else Icon(Icons.AutoMirrored.Outlined.Send,"Send reply")
                }
            }
            if(draft.length>1700) Text("${draft.length} / 2000",color=Muted,fontSize=12.sp,modifier=Modifier.align(Alignment.End))
        }}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ErrorPanel(state.errors["thread"],state::loadThread)
            if(thread==null) {
                if("thread" in state.busy) InitialLoad("Loading conversation")
                else EmptyPanel("Conversation unavailable","Use Refresh to try again.")
            } else if(thread.messages.isEmpty()) EmptyPanel("No messages yet","The conversation will appear here.")
            else LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,contentPadding=PaddingValues(horizontal=22.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                items(thread.messages,key={it.id}) { message -> MessageBubble(message) }
            }
        }
    }
    if(delete) AlertDialog(onDismissRequest={if(!actionBusy) delete=false},icon={Icon(Icons.Outlined.DeleteForever,null,tint=Danger)},
        title={Text("Delete this feedback?")},text={Text("Permanently delete this thread and every message in it. This cannot be undone.")},
        confirmButton={TextButton(enabled=!actionBusy,onClick={state.deleteThread {delete=false;onBack()}}){Text(if(actionBusy) "Deleting…" else "Delete permanently",color=Danger)}},
        dismissButton={TextButton(enabled=!actionBusy,onClick={delete=false}){Text("Cancel")}})
}
@Composable private fun MessageBubble(message: AdminRepository.FeedbackMessage) {
    val admin = message.fromDeveloper()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(admin) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max=420.dp).fillMaxWidth(.91f).clip(RoundedCornerShape(topStart=20.dp,topEnd=20.dp,
            bottomStart=if(admin) 20.dp else 4.dp,bottomEnd=if(admin) 4.dp else 20.dp)).background(if(admin) androidx.compose.ui.graphics.Color(0xFF0B2932) else Panel)
            .padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Eyebrow(if(admin) "ZEROCHILL / ADMIN" else "USER",color=if(admin) Cyan else Muted)
            SelectionContainer {Text(message.message,style=MaterialTheme.typography.bodyLarge)}
            Text(dateLabel(message.createdAt),color=Muted,fontSize=12.sp)
            if(admin) Text(if(message.isRead()) "Seen by user · ${dateLabel(message.readAt)}" else "Sent · not read yet",color=if(message.isRead()) Cyan else Muted,fontSize=12.sp)
        }
    }
}
