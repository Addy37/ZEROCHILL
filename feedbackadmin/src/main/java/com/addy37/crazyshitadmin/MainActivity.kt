package com.addy37.crazyshitadmin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {
    private val state: ConsoleState by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if(savedInstanceState == null) state.route(intent.getStringExtra(EXTRA_DESTINATION))
        setContent { ConsoleTheme { ConsoleApp(state) } }
        if(Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); state.route(intent.getStringExtra(EXTRA_DESTINATION)) }
    override fun onResume() { super.onResume(); if(state.paired) state.loadFeedback() }
    companion object { const val EXTRA_DESTINATION = "destination" }
}
/** Preserve the old internal Activity entry point without retaining the old UI. */
class AnalyticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, "analytics")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish()
    }
}
@Composable internal fun ConsoleApp(state: ConsoleState, threadBack: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var settings by rememberSaveable { mutableStateOf(false) }
    var lock by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val screenState = rememberSaveableStateHolder()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(state.paired) { if(state.paired) { state.refresh(); if(state.threadId.isNotEmpty()) state.loadThread() } }
    LaunchedEffect(state.notice) { if(state.notice.isNotEmpty()) { snackbar.showSnackbar(state.notice); state.consumeNotice() } }
    BackHandler(state.paired && (state.destination != "home" || state.threadId.isNotEmpty())) {
        if(state.threadId.isNotEmpty()) state.closeThread() else state.navigate("home")
    }
    if(!state.paired) {
        LaunchedEffect(Unit) { destinations.forEach { screenState.removeState(it.id) } }
        PairingScreen(state); return
    }
    if(state.threadId.isNotEmpty()) { ThreadScreen(state, onBack={state.closeThread();threadBack?.invoke()}); return }
    Scaffold(modifier=Modifier.imePadding(),containerColor=Ink, snackbarHost={ SnackbarHost(snackbar) },
        topBar={ Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start=22.dp, end=8.dp, top=10.dp, bottom=8.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Wordmark()
                    Text("Private admin session", color=Muted, style=MaterialTheme.typography.bodySmall)
                }
                ActionIcon(Icons.Outlined.Refresh, "Refresh", state.busy.isEmpty()) {
                    when(state.destination) { "feedback" -> state.loadFeedback(); "analytics" -> state.loadAnalytics();
                        "sources" -> state.loadConfig(force=true); else -> state.refresh() }
                }
                ActionIcon(Icons.Outlined.AdminPanelSettings, "Admin settings") { settings=true }
            }
            BusyLine(state.busy.isNotEmpty())
        } },
        bottomBar={ ConsoleNav(state.destination, state.items.count { it.unreadCount > 0 }) { focus.clearFocus(); state.navigate(it) } }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(state.destination, transitionSpec={ (fadeIn() + slideInHorizontally { it/16 }) togetherWith fadeOut() }, label="destination") { destination ->
                screenState.SaveableStateProvider(destination) { when(destination) {
                    "feedback" -> InboxScreen(state)
                    "sources" -> SourcesScreen(state)
                    "analytics" -> AnalyticsScreen(state)
                    else -> HomeScreen(state)
                } }
            }
        }
    }
    if(settings) AlertDialog(onDismissRequest={settings=false}, icon={ Icon(Icons.Outlined.AdminPanelSettings, null, tint=Cyan) },
        title={ Text("Admin session") }, text={ Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("ZeroChill Admin ${BuildConfig.VERSION_NAME}")
            Text("Notifications: " + if(NotificationManagerCompat.from(context).areNotificationsEnabled()) "Allowed" else "Off in Android settings", color=Muted)
            Text("Lock removes the admin token from this device. You will need it to connect again.", color=Muted)
        } }, confirmButton={ TextButton(onClick={settings=false;lock=true}) { Text("Lock session", color=Danger) } },
        dismissButton={ TextButton(onClick={settings=false}) { Text("Close") } })
    if(lock) AlertDialog(onDismissRequest={lock=false}, title={Text("Lock admin app?")},
        text={Text("Remove the saved token from this device?")}, confirmButton={TextButton(onClick={lock=false;state.lock()}) {Text("Lock",color=Danger)}},
        dismissButton={TextButton(onClick={lock=false}){Text("Cancel")}})
}
@Composable private fun PairingScreen(state: ConsoleState) {
    // Credentials are intentionally never placed in saved-instance state.
    var token by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val connecting = "pair" in state.busy
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Box(Modifier.fillMaxSize().background(Ink)) {
        EnergyField(Modifier.fillMaxWidth().height(340.dp).align(Alignment.TopCenter))
        Column(Modifier.align(Alignment.Center).widthIn(max=500.dp).fillMaxWidth().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement=Arrangement.spacedBy(22.dp)) {
            Image(painterResource(R.drawable.ic_admin_foreground), contentDescription=null, modifier=Modifier.size(92.dp))
            Wordmark()
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Eyebrow("PRIVATE CONTROL CENTER", color=Cyan)
                Text("Your console.\nFull control.", style=MaterialTheme.typography.headlineLarge)
                Text("Connect to manage feedback, source configuration, and aggregate app activity.", color=Muted)
            }
            OutlinedTextField(value=token, onValueChange={token=it}, label={Text("Admin token")}, singleLine=true,
                enabled=!connecting, modifier=Modifier.fillMaxWidth(), shape=ConsoleShape,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password, autoCorrectEnabled=false),
                visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon={ActionIcon(if(visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if(visible) "Hide token" else "Show token") {visible=!visible}})
            ErrorPanel(state.errors["pair"])
            Button(onClick={keyboard?.hide();state.connect(token)}, enabled=!connecting && token.isNotBlank(), modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {
                if(connecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth=2.dp, color=Ink)
                else Icon(Icons.Outlined.Key, null)
                Spacer(Modifier.width(10.dp)); Text(if(connecting) "Connecting…" else "Connect securely")
            }
            Text("Stored with Android Keystore on this device.", color=Muted, style=MaterialTheme.typography.bodySmall)
        }
    }
}
