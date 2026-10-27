package com.addy37.crazyshitadmin

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONObject

/** UI-only state. Network, authentication, storage and backend contracts remain in the Java layer. */
internal class ConsoleState @JvmOverloads constructor(application: Application, private val saved: SavedStateHandle,
    private val gateway: AdminGateway = RepositoryGateway) : AndroidViewModel(application) {
    var paired by mutableStateOf(SecureTokenStore.read(application).isNotEmpty()); private set
    var destination by mutableStateOf(saved.get<String>("destination") ?: "home"); private set
    var items by mutableStateOf<List<AdminRepository.Item>>(emptyList()); private set
    var analytics by mutableStateOf<AdminRepository.AnalyticsDashboard?>(null); private set
    var activeCounts by mutableStateOf<AdminRepository.ActiveCounts?>(null); private set
    var config by mutableStateOf<ConfigDraft?>(null); private set
    var history by mutableStateOf<List<AdminRepository.ConfigVersion>>(emptyList()); private set
    var thread by mutableStateOf<AdminRepository.FeedbackThread?>(null); private set
    var threadStatus by mutableStateOf(""); private set
    var threadId by mutableStateOf(saved.get<String>("threadId") ?: ""); private set

    var communityOverview by mutableStateOf<CommunityRepository.Overview?>(null); private set
    var communityAccounts by mutableStateOf<List<CommunityRepository.Account>>(emptyList()); private set
    var communityActivity by mutableStateOf<List<CommunityRepository.SocialActivity>>(emptyList()); private set
    var communityReports by mutableStateOf<List<CommunityRepository.Report>>(emptyList()); private set
    var accountDetail by mutableStateOf<CommunityRepository.AccountDetail?>(null); private set
    var communitySection by mutableStateOf(saved.get<String>("communitySection") ?: "accounts"); private set
    var communityQuery by mutableStateOf(saved.get<String>("communityQuery") ?: ""); private set
    private var communityFilterValue by mutableStateOf(saved.get<String>("communityFilter") ?: "all")
    val communityFilter: String get() = communityFilterValue

    var busy by mutableStateOf(setOf<String>()); private set
    var errors by mutableStateOf(mapOf<String, String>()); private set
    var notice by mutableStateOf(""); private set
    var refreshed by mutableStateOf(""); private set
    var sourceNotice by mutableStateOf(""); private set
    private var generation = 0
    private var tasks = mutableMapOf<String, Job>()
    private val requestIds = mutableMapOf<String, Int>()
    init {
        // The private, data-only candidate survives process recreation; never save credentials here.
        val text = saved.get<String>("configText")
        val base = saved.get<String>("configBase")
        if (paired && text != null && base != null) runCatching {
            config = ConfigDraft(base, text, saved.get<Long>("configPublished") ?: 0)
        }
    }
    fun navigate(value: String) { destination = value; saved["destination"] = value }
    fun route(value: String?) { if (value != null) navigate(if(value == "dashboard") "home" else value) }
    private fun persistConfig() {
        saved["configText"] = config?.text; saved["configBase"] = config?.baseline
        saved["configPublished"] = config?.published
    }
    private fun request(key: String, block: suspend (String) -> Unit) {
        val replaceable = key == "thread" || key == "communityList" || key == "accountDetail"
        if (key in busy && !replaceable) return
        if (replaceable) tasks.remove(key)?.cancel()
        val requestId = (requestIds[key] ?: 0) + 1
        requestIds[key] = requestId
        val epoch = generation
        val token = SecureTokenStore.read(getApplication())
        busy = busy + key; errors = errors - key
        tasks[key] = viewModelScope.launch {
            try { block(token) }
            catch (e: CancellationException) { throw e }
            catch (e: SecurityException) { if (generation == epoch) { lock(); errors = mapOf("pair" to if(key == "pair") "Authentication failed. Check your admin token." else "Your admin session expired. Connect again.") } }
            catch (e: Exception) { if (generation == epoch) errors = errors + (key to (e.message ?: "Request failed. Try again.")) }
            finally { if (generation == epoch && requestIds[key] == requestId) { busy = busy - key; tasks.remove(key) } }
        }
    }
    fun connect(token: String) {
        if (token.trim().length < 24) { errors = errors + ("pair" to "Paste the complete admin token."); return }
        request("pair") {
            val loaded = withContext(Dispatchers.IO) { gateway.list(token.trim()) }
            withContext(Dispatchers.IO) { SecureTokenStore.save(getApplication(), token.trim()) }
            paired = true; items = loaded
            (getApplication<Application>() as AdminApplication).scheduleNotifications()
            navigate("home")
        }
    }
    fun lock() {
        generation++; tasks.values.toList().forEach { it.cancel() }; tasks.clear()
        SecureTokenStore.clear(getApplication()); paired = false
        items = emptyList(); analytics = null; activeCounts = null; config = null; history = emptyList(); thread = null
        communityOverview = null; communityAccounts = emptyList(); communityActivity = emptyList(); communityReports = emptyList(); accountDetail = null
        communitySection = "accounts"; communityQuery = ""; communityFilterValue = "all"
        saved["communitySection"] = communitySection; saved["communityQuery"] = communityQuery; saved["communityFilter"] = communityFilterValue
        threadId = ""; saved["threadId"] = ""; busy = emptySet(); errors = emptyMap(); notice = ""
        saved.keys().filter { it.startsWith("reply:") }.forEach { saved.remove<String>(it) }
        persistConfig(); navigate("home")
    }
    fun refresh() { loadFeedback(); loadAnalytics(); loadConfig() }
    fun loadFeedback() = request("feedback") { token ->
        items = withContext(Dispatchers.IO) { gateway.list(token) }; refreshed = java.time.Instant.now().toString()
    }
    fun loadAnalytics() = request("analytics") { token ->
        val loaded = withContext(Dispatchers.IO) { gateway.analytics(token) }
        analytics = loaded
        activeCounts = AdminRepository.ActiveCounts(loaded)
    }
    fun loadActiveCounts() = request("activeCounts") { token ->
        activeCounts = withContext(Dispatchers.IO) { gateway.activeCounts(token) }
    }

    fun loadCommunityOverview() = request("communityOverview") { token ->
        communityOverview = withContext(Dispatchers.IO) { gateway.communityOverview(token) }
    }
    fun refreshCommunity() { loadCommunityOverview(); loadCommunityList() }
    fun navigateCommunitySection(value: String) {
        communitySection = value; communityQuery = ""; communityFilterValue = "all"; accountDetail = null
        saved["communitySection"] = value; saved["communityQuery"] = ""; saved["communityFilter"] = "all"
        loadCommunityList()
    }
    fun searchCommunity(value: String) {
        communityQuery = value.trim(); saved["communityQuery"] = communityQuery; loadCommunityList()
    }
    fun setCommunityFilter(value: String) {
        communityFilterValue = value; saved["communityFilter"] = value; loadCommunityList()
    }
    fun loadCommunityList() {
        val section = communitySection
        val query = communityQuery
        val filter = communityFilterValue
        request("communityList") { token ->
            when(section) {
                "social" -> {
                    val loaded = withContext(Dispatchers.IO) { gateway.socialActivity(token, query, filter) }
                    if(communitySection == section && communityQuery == query && communityFilterValue == filter) communityActivity = loaded
                }
                "reports" -> {
                    val loaded = withContext(Dispatchers.IO) { gateway.reports(token, query, filter) }
                    if(communitySection == section && communityQuery == query && communityFilterValue == filter) communityReports = loaded
                }
                else -> {
                    val loaded = withContext(Dispatchers.IO) { gateway.accounts(token, query, filter) }
                    if(communitySection == section && communityQuery == query && communityFilterValue == filter) communityAccounts = loaded
                }
            }
        }
    }
    fun loadAccountDetail(userId: String) = request("accountDetail") { token ->
        accountDetail = withContext(Dispatchers.IO) { gateway.accountDetail(token, userId) }
    }
    fun closeAccountDetail() { tasks.remove("accountDetail")?.cancel(); accountDetail = null; errors = errors - "accountDetail" }

    fun loadConfig(force: Boolean = false) {
        if (config != null && !force) return
        request("sources") { token ->
            val item = withContext(Dispatchers.IO) { gateway.currentConfig(token) }
            val document = item?.optJSONObject("config") ?: withContext(Dispatchers.IO) {
                getApplication<Application>().assets.open("source_config_defaults.json").bufferedReader().use { JSONObject(it.readText()) }
            }
            // A late refresh must never overwrite edits made while the request was running.
            if (config == null || force && config?.dirty != true) {
                config = ConfigDraft(document.toString(), document.toString(), item?.optLong("config_version") ?: 0)
                sourceNotice = if(item == null) "Bundled defaults · no published configuration" else "Published configuration loaded"
                persistConfig()
            }
        }
    }
    fun editConfig(value: JSONObject) { if ("configAction" in busy) return; config = config?.edit(value); sourceNotice = "Unpublished changes · validate before publishing"; persistConfig() }
    fun discardConfig() { if ("configAction" in busy) return; config = config?.discard(); sourceNotice = "Changes discarded"; persistConfig() }
    fun validateConfig() {
        val draft = config ?: return
        val candidate = draft.candidate()
        request("configAction") { token ->
            withContext(Dispatchers.IO) { gateway.validateConfig(token, JSONObject(candidate)) }
            if (config == draft) { config = draft.acceptValidation(candidate); sourceNotice = "Validated · ready to publish"; persistConfig() }
        }
    }
    fun publishConfig() {
        val draft = config?.takeIf { it.canPublish } ?: return
        request("configAction") { token ->
            val version = withContext(Dispatchers.IO) { gateway.publishConfig(token, JSONObject(draft.validated)) }
            config = ConfigDraft(draft.text, draft.text, version)
            sourceNotice = "Version $version published"; notice = sourceNotice; persistConfig()
        }
    }
    fun loadHistory() = request("history") { token -> history = withContext(Dispatchers.IO) { gateway.configHistory(token) } }
    fun rollback(version: Long) {
        if (config?.dirty == true) { errors = errors + ("history" to "Discard or publish your candidate before rolling back."); return }
        request("configAction") { token ->
            val next = withContext(Dispatchers.IO) { gateway.rollbackConfig(token, version) }
            config = null; persistConfig(); loadConfig(); loadHistory(); notice = "Restored version $version as version $next"
        }
    }
    fun openThread(id: String) { threadId = id; saved["threadId"] = id; if (thread?.item?.id != id) thread = null; loadThread() }
    fun closeThread() { tasks.remove("thread")?.cancel(); threadId = ""; saved["threadId"] = ""; thread = null; loadFeedback() }
    fun loadThread() {
        val id = threadId.takeIf { it.isNotEmpty() } ?: return
        request("thread") { token ->
            val loaded = withContext(Dispatchers.IO) { gateway.thread(token, id) }
            if (threadId == id) { thread = loaded; threadStatus = loaded.item.status }
        }
    }
    fun updateStatus(status: String) {
        val prior = thread ?: return
        request("threadAction") { token ->
            // Keep the confirmed status until the server accepts the change.
            withContext(Dispatchers.IO) { gateway.updateStatus(token, prior.item.id, status) }
            notice = "Status updated to ${statusLabel(status)}"
            if (threadId == prior.item.id) { threadStatus = status; loadThread() } else loadFeedback()
        }
    }
    fun draft(id: String): String = saved["reply:$id"] ?: ""
    fun setDraft(id: String, value: String) { saved["reply:$id"] = value.take(2000) }
    fun sendReply(value: String) {
        val current = thread ?: return
        if (value.trim().isEmpty() || value.length > 2000) return
        request("threadAction") { token ->
            withContext(Dispatchers.IO) { gateway.reply(token, current.item.id, threadStatus.ifEmpty {current.item.status}, value.trim()) }
            setDraft(current.item.id, ""); notice = "Reply sent"; loadThread()
        }
    }
    fun deleteThread(done: () -> Unit) {
        val id = threadId
        request("threadAction") { token ->
            withContext(Dispatchers.IO) { gateway.deleteFeedback(token, id) }
            saved.remove<String>("reply:$id"); notice = "Feedback deleted"
            if (threadId == id) { closeThread(); done() } else loadFeedback()
        }
    }
    fun consumeNotice() { notice = "" }
}
