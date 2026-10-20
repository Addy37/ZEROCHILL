package com.addy37.crazyshitadmin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun SourcesScreen(state: ConsoleState) {
    val draft = state.config
    var editing by rememberSaveable {mutableStateOf<String?>(null)}
    var history by rememberSaveable {mutableStateOf(false)}
    var publish by rememberSaveable {mutableStateOf(false)}
    var discard by rememberSaveable {mutableStateOf(false)}
    val actionBusy = "configAction" in state.busy
    val document = remember(draft?.text) { draft?.let {JSONObject(it.text)} }
    val sources = document?.optJSONObject("sources")
    val keys = remember(draft?.text) {sources?.keysList().orEmpty()}
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val sourceColumns = if(maxWidth>680.dp) 2 else 1
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("source-list"),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Eyebrow("03 / REMOTE CONTROL",color=Cyan);Spacer(Modifier.height(8.dp))
                Text("Sources",style=MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp));Text("Edit a candidate. Validate it. Publish when ready.",color=Muted)
            }
            item { ErrorPanel(state.errors["sources"],{state.loadConfig()}) }
            if(draft==null && "sources" in state.busy) item {InitialLoad("Loading source configuration")}
            if(draft!=null && document!=null) {
                item {ConsolePanel(Modifier.fillMaxWidth(),accent=true) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {Eyebrow("PUBLISHED");Text(if(draft.published>0) "v${draft.published}" else "None",style=MaterialTheme.typography.headlineLarge)}
                        TextButton(onClick={history=true;state.loadHistory()}) {Icon(Icons.Outlined.History,null);Spacer(Modifier.width(6.dp));Text("History")}
                    }
                    Text(state.sourceNotice,color=Muted)
                    Text("${keys.size} configured providers · runtime health unknown",color=Muted,fontSize=13.sp)
                    if(draft.dirty) StatusTag(if(draft.canPublish) "Candidate v${JSONObject(draft.text).optLong("configVersion")} · validated" else "Candidate v${draft.candidateVersion} · not validated")
                } }
                item { Eyebrow("GLOBAL CONTROLS") }
                val global = document.optJSONObject("global")
                if(global!=null) item { Column {
                    ToggleSetting("Source kill switches","Remote availability controls",global.optBoolean("sourceKillSwitchesEnabled",true),!actionBusy) {value ->
                        state.editConfig(JSONObject(draft.text).apply {getJSONObject("global").put("sourceKillSwitchesEnabled",value)})
                    }
                    ToggleSetting("Fallback domains","Use configured fallback hosts",global.optBoolean("fallbacksEnabled",true),!actionBusy) {value ->
                        state.editConfig(JSONObject(draft.text).apply {getJSONObject("global").put("fallbacksEnabled",value)})
                    }
                } }
                item {Eyebrow("PROVIDERS")}
                items(keys.chunked(sourceColumns),key={it.joinToString()}) {rowKeys ->
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        rowKeys.forEach {key -> Column(Modifier.weight(1f)) {
                            val source = sources?.optJSONObject(key)
                            if(source!=null) SourceCard(key,source,!actionBusy,onEdit={editing=key}) {value ->
                                state.editConfig(JSONObject(draft.text).apply {getJSONObject("sources").getJSONObject(key).put("enabled",value)})
                            } else ErrorPanel("Config problem · $key must be an object")
                        } }
                        if(rowKeys.size<sourceColumns) Spacer(Modifier.weight(1f))
                    }
                }
                item { TextButton(onClick={editing="__full__"},enabled=!actionBusy) {Icon(Icons.Outlined.DataObject,null);Spacer(Modifier.width(8.dp));Text("Advanced · full configuration JSON")} }
            }
        }
        if(draft!=null) Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            ErrorPanel(state.errors["configAction"])
            Text(if(actionBusy) "Applying request…" else if(draft.canPublish) "Validated candidate · ready for review" else if(draft.dirty) "Unpublished changes · validation required" else "No unpublished changes",color=if(draft.canPublish) Good else Muted,fontSize=13.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={discard=true},enabled=draft.dirty && !actionBusy) {Text("Discard")}
                OutlinedButton(onClick=state::validateConfig,enabled=!actionBusy,modifier=Modifier.weight(1f)) {Text("Validate")}
                Button(onClick={publish=true},enabled=draft.canPublish && !actionBusy,modifier=Modifier.weight(1f)) {Text("Publish")}
            }
        }
    }
    }
    if(editing!=null && draft!=null) {
        val key = editing!!
        val full = key=="__full__"
        val original = if(full) JSONObject(draft.text) else JSONObject(draft.text).getJSONObject("sources").getJSONObject(key)
        ConfigEditorDialog(if(full) "Full configuration" else sourceName(key),original,full,onDismiss={editing=null}) {replacement ->
            val next = if(full) replacement else JSONObject(draft.text).apply {getJSONObject("sources").put(key,replacement)}
            state.editConfig(next);editing=null
        }
    }
    if(publish && draft!=null) AlertDialog(onDismissRequest={publish=false},title={Text("Publish candidate?")},text={
        Column(Modifier.heightIn(max=330.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Publish version ${JSONObject(draft.text).optLong("configVersion")} to all apps. Phones normally check within 45 minutes.")
            Eyebrow("CHANGED SETTINGS")
            val changes = draft.changes()
            if(changes.isEmpty()) Text("Version and publication timestamp only.",color=Muted)
            changes.forEach {Text(it.removePrefix("sources."),fontSize=13.sp,color=Muted)}
        }
    },confirmButton={TextButton(onClick={publish=false;state.publishConfig()},enabled=draft.canPublish && !actionBusy){Text("Publish version")}},
        dismissButton={TextButton(onClick={publish=false}){Text("Cancel")}})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("Discard candidate changes?")},text={Text("Return to the configuration loaded from the server. Nothing will be published.")},
        confirmButton={TextButton(onClick={discard=false;state.discardConfig()}){Text("Discard",color=Danger)}},dismissButton={TextButton(onClick={discard=false}){Text("Keep editing")}})
    if(history) HistoryDialog(state) {history=false}
}
internal fun sourceName(key: String) = when(key) {"wikifeet"->"WikiFeet";"wikifeetx"->"WikiFeet X";"theync"->"TheYNC";"itemfix"->"ItemFix";"onlyhaven"->"OnlyHaven";"baddiehub"->"BaddieHub";else->friendly(key)}
@Composable private fun SourceCard(key: String, source: JSONObject, enabled: Boolean, onEdit: () -> Unit, toggle: (Boolean) -> Unit) {
    val on = source.optBoolean("enabled",true)
    val endpoint = source.optString("baseUrl", source.optString("indexUrl", ""))
    val problem = endpoint.isBlank()
    Column(Modifier.fillMaxWidth().clip(ConsoleShape).background(Panel).clickable(enabled=enabled,onClick=onEdit).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(sourceName(key),style=MaterialTheme.typography.titleLarge)
                StatusTag(if(problem) "Config problem" else if(on) "Enabled" else "Disabled",if(problem) Danger else if(on) Cyan else Muted)
            }
            Switch(on,toggle,enabled=enabled,modifier=Modifier.semanticsForSwitch(sourceName(key)))
        }
        Text(endpoint.removePrefix("https://").trimEnd('/').ifEmpty {"No endpoint configured"},color=Muted,fontSize=14.sp)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("${source.optInt("retryCount")} retries · ${source.optInt("requestTimeoutMs") / 1000f}s timeout",Modifier.weight(1f),color=Muted,fontSize=13.sp)
            Icon(Icons.Outlined.ChevronRight,"Edit ${sourceName(key)}",tint=Cyan)
        }
    }
}
private fun Modifier.semanticsForSwitch(name: String) = this.then(Modifier.semantics { contentDescription = "$name enabled" })
@Composable private fun ToggleSetting(title: String, detail: String, value: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(title);Text(detail,color=Muted,fontSize=13.sp)}
        Switch(value,change,enabled=enabled,modifier=Modifier.semanticsForSwitch(title))
    }
}
private fun fieldGroup(key: String): String = when {
    key.contains("timeout",true) || key.contains("retry",true) -> "Network & timeouts"
    key.contains("header",true) || key.contains("agent",true) || key.contains("referer",true) -> "Headers"
    key.contains("url",true) || key.contains("domain",true) || key.contains("origin",true) || key.contains("host",true) || key.contains("endpoint",true) || key.endsWith("Root") -> "Domains & endpoints"
    else -> "General"
}
private fun fieldLabel(key: String) = key.replace(Regex("([a-z])([A-Z])"),"$1 $2").replaceFirstChar {it.titlecase()}
@Composable private fun ConfigEditorDialog(title: String, original: JSONObject, full: Boolean, onDismiss: () -> Unit, apply: (JSONObject) -> Unit) {
    val initial = remember {original.toString()}
    var raw by rememberSaveable {mutableStateOf(original.toString(2))}
    var local by rememberSaveable {mutableStateOf(initial)}
    var advanced by rememberSaveable {mutableStateOf(full)}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    var confirmClose by remember {mutableStateOf(false)}
    var invalidKeys by rememberSaveable {mutableStateOf(listOf<String>())}
    var fieldRevision by rememberSaveable {mutableStateOf(0)}
    val value = remember(local) {JSONObject(local)}
    val groups = remember(initial) {original.keysList().filter {it != "enabled" && original.opt(it) !is JSONObject &&
        (original.opt(it) !is JSONArray || (0 until original.getJSONArray(it).length()).all {index -> original.getJSONArray(it).opt(index) is String})}.groupBy(::fieldGroup)}
    val close = { if(local != initial || raw != original.toString(2)) confirmClose=true else onDismiss() }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.systemBarsPadding().imePadding().padding(10.dp).widthIn(max=740.dp).fillMaxWidth().fillMaxHeight(),shape=ConsoleShape,color=Ink) {
            Column {
                Row(Modifier.padding(start=20.dp,end=8.dp,top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Eyebrow("CANDIDATE EDITOR",color=Cyan);Text(title,style=MaterialTheme.typography.headlineMedium)}
                    ActionIcon(Icons.Outlined.Close,"Close editor",onClick=close)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    item {ErrorPanel(error)}
                    if(!advanced) {
                        if(value.has("enabled")) item {ToggleSetting("Enabled","Applies only after publishing",value.optBoolean("enabled",true),true) { local=JSONObject(local).put("enabled",it).toString() }}
                        groups.forEach {(group,keys)->
                            item {Eyebrow(group,color=Cyan)}
                            items(keys,key={it}) {key ->
                                val field = value.opt(key)
                                if(field is Boolean) ToggleSetting(fieldLabel(key),"",field,true) {local=JSONObject(local).put(key,it).toString()}
                                else androidx.compose.runtime.key(fieldRevision,key) { ConfigField(key,original.opt(key),field,onValidity={ valid -> invalidKeys=if(valid) invalidKeys-key else (invalidKeys+key).distinct() }) {next -> local=JSONObject(local).put(key,next).toString()} }
                            }
                        }
                        item {TextButton(onClick={raw=JSONObject(local).toString(2);advanced=true;error=null}) {Icon(Icons.Outlined.Code,null);Spacer(Modifier.width(8.dp));Text("Advanced · all fields as JSON")}}
                    } else {
                        item {Text("All configuration fields remain available here. JSON must parse before it can become a candidate. Server validation is still required.",color=Muted)}
                        item {OutlinedTextField(raw,{raw=it;error=null},Modifier.fillMaxWidth().heightIn(min=320.dp),
                            textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace),label={Text("Advanced JSON")},isError=error!=null)}
                        if(!full) item {TextButton(onClick={runCatching {JSONObject(raw)}.onSuccess {local=it.toString();advanced=false;error=null;fieldRevision++;invalidKeys=emptyList()}.onFailure {error="Invalid JSON: ${it.message}"}}) {Text("Return to guided fields")}}
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick=close) {Text("Cancel")}
                    Button(enabled=advanced || invalidKeys.isEmpty(),onClick={runCatching {if(advanced) JSONObject(raw) else JSONObject(local)}.onSuccess(apply).onFailure {error="Invalid JSON: ${it.message}"}},modifier=Modifier.weight(1f)) {Text("Apply to candidate")}
                }
            }
        }
    }
    if(confirmClose) AlertDialog(onDismissRequest={confirmClose=false},title={Text("Discard editor changes?")},text={Text("These fields have not been applied to the candidate yet.")},
        confirmButton={TextButton(onClick=onDismiss){Text("Discard",color=Danger)}},dismissButton={TextButton(onClick={confirmClose=false}){Text("Keep editing")}})
}
@Composable private fun ConfigField(key: String, original: Any?, value: Any?, onValidity: (Boolean) -> Unit, change: (Any) -> Unit) {
    val isNumber = original is Number
    val isList = original is JSONArray
    val external = if(value is JSONArray) (0 until value.length()).joinToString("\n") {value.optString(it)} else value?.toString().orEmpty()
    var text by rememberSaveable(key) {mutableStateOf(external)}
    var invalid by rememberSaveable(key) {mutableStateOf(false)}
    OutlinedTextField(text,{ next ->
        text=next
        onValidity(!isNumber || next.toLongOrNull()!=null)
        when {
            isNumber -> {invalid=next.toLongOrNull()==null;if(!invalid) change(next.toLong())}
            isList -> {invalid=false;change(JSONArray(next.replace(',','\n').lines().map {it.trim()}.filter {it.isNotEmpty()}))}
            else -> {invalid=false;change(next)}
        }
    },Modifier.fillMaxWidth(),label={Text(fieldLabel(key))},supportingText={if(invalid) Text("Enter a whole number. Correct this field before applying changes.") else if(isList) Text("One entry per line")},
        isError=invalid,keyboardOptions=KeyboardOptions(keyboardType=if(isNumber) KeyboardType.Number else KeyboardType.Text,autoCorrectEnabled=false),minLines=if(isList) 2 else 1)
}
@Composable private fun HistoryDialog(state: ConsoleState, dismiss: () -> Unit) {
    var target by remember {mutableStateOf<Long?>(null)}
    val busy = "configAction" in state.busy
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.widthIn(max=640.dp).fillMaxWidth().fillMaxHeight(.85f).padding(16.dp),shape=ConsoleShape,color=Ink) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {Text("Config history",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);ActionIcon(Icons.Outlined.Close,"Close history",onClick=dismiss)}
                Text("Rollback republishes a previous snapshot as a new higher version.",color=Muted)
                ErrorPanel(state.errors["history"],state::loadHistory)
                ErrorPanel(state.errors["configAction"])
                BusyLine("history" in state.busy || busy)
                LazyColumn(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    if(state.history.isEmpty() && "history" !in state.busy) item {EmptyPanel("No history loaded","Published versions will appear here.",Icons.Outlined.History)}
                    items(state.history,key={it.version}) {v ->ConsolePanel {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("Version ${v.version}",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                            if(v.active) StatusTag("Active",Good)
                        }
                        Text("${friendly(v.action)} · ${dateLabel(v.updatedAt)}",color=Muted)
                        Text("Based on version ${v.basedOnVersion}",color=Muted,fontSize=13.sp)
                        if(!v.active) OutlinedButton(enabled=!busy && state.config?.dirty!=true && v.version<(state.config?.published ?: 0),onClick={target=v.version}) {Text("Roll back to this version")}
                    } }
                }
            }
        }
    }
    if(target!=null) AlertDialog(onDismissRequest={target=null},title={Text("Roll back configuration?")},text={Text("Republish version $target as a new higher version for every app.")},
        confirmButton={TextButton(onClick={state.rollback(target!!);target=null}){Text("Roll back")}},dismissButton={TextButton(onClick={target=null}){Text("Cancel")}})
}
