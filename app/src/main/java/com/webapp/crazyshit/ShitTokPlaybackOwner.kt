@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.webapp.crazyshit

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.os.Trace
import androidx.media3.common.*
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/** Feed-lifetime, main-thread owner. RecyclerView holders borrow surfaces, never engines. */
class ShitTokPlaybackOwner(context: Context, private val callbacks: Callbacks) {
    interface Callbacks {
        fun onLeaseChanged(url: String)
        fun onLeaseRetired(url: String)
    }
    fun interface PlayerFactory { fun create(): ExoPlayer }
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pool: PlayerPool<ExoPlayer>? = null
    private var manager: DefaultPreloadManager? = null
    private var builder: DefaultPreloadManager.Builder? = null
    private val control = PoolLoadControl()
    private val protection = ShitTokBufferProtection()
    @Volatile private var speculativeAllowed = true
    private var speculationNotification: Job? = null
    private var speculationListener: java.util.function.Consumer<Boolean>? = null
    fun setSpeculationListener(listener: java.util.function.Consumer<Boolean>) { speculationListener = listener }
    private var continuityFailureListener: java.util.function.Consumer<String>? = null
    fun setContinuityFailureListener(listener: java.util.function.Consumer<String>) { continuityFailureListener = listener }
    private var healthCheck: Job? = null
    private var bandwidthEstimate = -1L
    private val leases = linkedMapOf<String, Lease>()
    private val positions = linkedMapOf<String, Long>()
    private val paused = mutableSetOf<String>()
    private val sources = linkedMapOf<String, MediaSource>()
    private val waitingSources = linkedMapOf<String, Pair<CrazyShitRepository.StreamInfo, Int>>()
    private val recoveryHandled = java.util.IdentityHashMap<ExoPlayer, PlaybackException>()
    private val waitingErrors = java.util.IdentityHashMap<ExoPlayer, PlaybackException>()
    private var acquisition: Job? = null
    private var epoch = 0
    private var window = emptyList<String>()
    private var selected: String? = null
    private var immediateNext: String? = null
    private var continuityReady = false
    private var permitted = false
    private var muted = false
    private data class SpeedBoost(val player: ExoPlayer, val previous: PlaybackParameters, val ended: Runnable)
    private var speedBoost: SpeedBoost? = null

    /** Temporary interaction on the current attached lease, without changing playback intent. */
    fun beginSpeedBoost(url: String, expected: ExoPlayer, ended: Runnable): Boolean {
        if (speedBoost != null || !canPlay(url) || leases[url]?.player !== expected ||
            leases[url]?.stream == null || !expected.isPlaying || expected.playerError != null) return false
        speedBoost = SpeedBoost(expected, expected.playbackParameters, ended)
        expected.setPlaybackSpeed(2f)
        return true
    }
    fun endSpeedBoost(expected: ExoPlayer) {
        if (speedBoost?.player === expected) endSpeedBoost()
    }
    private fun endSpeedBoost() {
        val boost = speedBoost ?: return
        speedBoost = null
        try { boost.player.playbackParameters = boost.previous } finally { boost.ended.run() }
    }
    fun setMuted(value: Boolean) {
        muted = value
        leases.forEach { (url, lease) -> lease.player.volume = if (canPlay(url) && !value) 1f else 0f }
    }
    private var preparedLimit = 3
    private var created = 0
    private val initializedAt = SystemClock.elapsedRealtime()
    private var firstPrepare = false
    private var firstFrame = false
    private var planCount = 0
    private var testFactory: PlayerFactory? = null
    private data class Lease(val player: ExoPlayer, val token: Int,
        var stream: CrazyShitRepository.StreamInfo? = null, var view: PlayerView? = null)

    // Called only before any engine is acquired, by decoder-free integration fixtures.
    fun setPlayerFactoryForTest(factory: PlayerFactory) { check(pool == null); testFactory = factory }

    private fun start() {
        if (pool != null) return
        if (testFactory == null) {
            builder = DefaultPreloadManager.Builder(app) { rank ->
                if (control.networkPaused || control.currentNeedsBuffer || control.starving || kotlin.math.abs(rank) <= 1)
                    DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
                else DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(0, 1_000)
            }.setLoadControl(control)
                .setRenderersFactory(DefaultRenderersFactory(app).setEnableDecoderFallback(true))
            manager = builder!!.build()
        }
        pool = PlayerPool(3) {
            (testFactory?.create() ?: builder!!.buildExoPlayer()).also { player ->
                created++
                player.volume = 0f
                player.playWhenReady = false
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(1920, 1080).setMaxVideoBitrate(8_000_000).build()
                player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onBandwidthEstimate(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
                        if (leases.values.any { it.player === player && selected != null && leases[selected]?.player === player })
                            bandwidthEstimate = bitrateEstimate
                    }
                })
                ShitTokPoolDiagnostics.record("create", "created=$created capacity=3")
            }
        }
    }

    /** No planning on scroll-state-only changes. Reconciliation uses settled IDs only. */
    fun select(current: String, next: String?, previous: String?, neighbors: Boolean) {
        Trace.beginSection("zc.shittok.pool_select")
        try { selectWindow(current, next, previous, neighbors) } finally { Trace.endSection() }
    }
    private fun selectWindow(current: String, next: String?, previous: String?, neighbors: Boolean) {
        val wanted = listOfNotNull(current, next.takeIf { neighbors }, previous.takeIf { neighbors })
            .distinct().take(preparedLimit)
        if (selected == current && window == wanted && pool != null) { syncAudio(); return }
        revokeAudio()
        if (selected != current) {
            bandwidthEstimate = -1L
            protection.select(current)
            control.starving = false
            control.recoveryTargetUs = 0L
            control.recoveryDeadlineMs = 0L
        }
        selected = current
        immediateNext = next
        if (control.continuityId != next) control.continuityId = null
        window = wanted
        planCount++
        start()
        acquisition?.cancel()
        val generation = ++epoch
        val owner = pool!!
        // Retire and yield before acquire. Detached RecyclerView views cannot grow the pool.
        leases.keys.toList().filter { it !in wanted }.forEach { retire(it) }
        sources.keys.toList().filter { it !in wanted }.forEach { removeSource(it) }
        control.currentId = current
        acquisition = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            wanted.forEach { url ->
                // Current acquisition stays immediate. Only real starvation defers neighbor creation.
                while (url != selected && control.starving && !allowsContinuityPreparation(url)) delay(120)
                if (url !in leases) {
                    val at = SystemClock.elapsedRealtime()
                    val player = owner.acquire()
                    if (pool !== owner || epoch != generation || url !in window) {
                        if (pool === owner) owner.yield(player)
                        return@launch
                    }
                    leases[url] = Lease(player, generation)
                    player.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (!owns(url, player, generation)) return
                            if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) endSpeedBoost(player)
                            updateBufferPriority()
                            if (state == Player.STATE_READY) ShitTokPoolDiagnostics.record("ready", url)
                            if (url == selected && state == Player.STATE_READY) {
                                AppPerformance.startup("ready")
                                flushWaitingSources()
                            }
                        }
                        override fun onRenderedFirstFrame() {
                            if (owns(url, player, generation)) {
                                ShitTokPoolDiagnostics.record("firstFrame", url)
                                if (url == selected && !firstFrame) {
                                    firstFrame = true
                                    AppPerformance.startup("frame")
                                    AppPerformance.record("ShitTok first rendered frame", SystemClock.elapsedRealtime() - initializedAt)
                                }
                            }
                        }
                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            if (!playWhenReady) endSpeedBoost(player)
                        }
                        override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                            if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) endSpeedBoost(player)
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying && (!owns(url, player, generation) || !canPlay(url))) silence(player)
                        }
                        override fun onEvents(observed: Player, events: Player.Events) {
                            if (owns(url, player, generation)) observeSelected(url, player)
                        }
                        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo, reason: Int) {
                            if (url == selected && owns(url, player, generation) &&
                                (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)) {
                                protection.interrupt(SystemClock.elapsedRealtime(), true)
                                publishProtection()
                            }
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            endSpeedBoost(player)
                            if (owns(url, player, generation)) scope.launch {
                                if (owns(url, player, generation) && !recoverDecoder(player, error)
                                    && url != selected && url == control.continuityId) {
                                    continuityFailureListener?.accept(url)
                                }
                            }
                        }
                    }.also { listeners[player] = it })
                    ShitTokPoolDiagnostics.record("acquire", "url=$url ms=${SystemClock.elapsedRealtime()-at} leases=${leases.size}")
                    callbacks.onLeaseChanged(url)
                }
            }
            syncAudio()
        }
    }
    private val listeners = java.util.IdentityHashMap<ExoPlayer, Player.Listener>()
    fun token(url: String): Int = leases[url]?.token ?: -1
    fun accepts(url: String, token: Int): Boolean = token >= 0 && leases[url]?.token == token
    private fun owns(url: String, player: ExoPlayer, token: Int) =
        leases[url]?.let { it.player === player && it.token == token } == true
    fun player(url: String): ExoPlayer? = leases[url]?.player
    fun stream(url: String): CrazyShitRepository.StreamInfo? = leases[url]?.stream
    fun isAttached(url: String, view: PlayerView): Boolean = leases[url]?.view === view
    fun isUserPaused(url: String): Boolean = url in paused
    fun setUserPaused(url: String, value: Boolean) { if (value) paused.add(url) else paused.remove(url); syncAudio() }

    fun permission(visibleResumedSettled: Boolean, isMuted: Boolean) {
        if (permitted == visibleResumedSettled && muted == isMuted) return
        revokeAudio()
        permitted = visibleResumedSettled
        muted = isMuted
        control.networkPaused = !permitted
        syncAudio()
        manager?.invalidate()
        if (!permitted) { protection.interrupt(SystemClock.elapsedRealtime(), false); publishProtection() }
        ensureHealthCheck()
        if (allowsSpeculativeWork()) flushWaitingSources()
        if (permitted) waitingErrors.toMap().forEach { (player, error) ->
            waitingErrors.remove(player)
            recoverDecoder(player, error)
        }
    }
    private fun canPlay(url: String) = permitted && url == selected && url !in paused && leases[url]?.view != null
    fun revokeAudio() { leases.values.forEach { silence(it.player) } }
    fun syncAudio() {
        leases.forEach { (url, lease) -> if (!canPlay(url)) silence(lease.player) }
        selected?.let { url -> leases[url]?.takeIf { canPlay(url) && it.stream != null }?.let {
            it.player.volume = if (muted) 0f else 1f
            if (it.player.playbackState != Player.STATE_ENDED && !it.player.playWhenReady) it.player.play()
        } }
        updateBufferPriority()
    }
    fun requestPlay(url: String) {
        if (!canPlay(url)) return
        syncAudio()
        leases[url]?.takeIf { it.stream != null }?.let {
            if (!it.player.playWhenReady || it.player.playbackState == Player.STATE_ENDED) it.player.play()
        }
    }
    private fun updateBufferPriority() {
        val current = selected?.let { leases[it] }
        control.currentNeedsBuffer = current == null || current.stream == null ||
            current.player.playbackState == Player.STATE_BUFFERING || current.player.playbackState == Player.STATE_IDLE
        publishProtection()
    }
    fun allowsSpeculativeWork(): Boolean = speculativeAllowed
    fun isStarvationProtected(): Boolean = protection.isProtected()
    /** Only the feed's immediate next URL gets the recovery exception, never the previous/deep window. */
    fun setContinuityNext(url: String?) {
        val next = url?.takeIf { it == immediateNext && it in window && it != selected }
        if (control.continuityId == next) return
        control.continuityId = next
        flushWaitingSources()
    }
    fun allowsContinuityPreparation(url: String): Boolean =
        permitted && !control.currentNeedsBuffer && url == control.continuityId && url == immediateNext
    private fun observeSelected(url: String, player: ExoPlayer) {
        if (url != selected) return
        protection.observe(SystemClock.elapsedRealtime(), player.playbackState, player.isPlaying,
            player.playWhenReady, canPlay(url), url in paused, player.playerError != null,
            player.currentPosition, player.totalBufferedDuration)
        updateBufferPriority()
        ensureHealthCheck()
    }
    private fun publishProtection() {
        control.starving = protection.isProtected()
        control.recoveryTargetUs = protection.recoveryTargetMs() * 1_000L
        control.recoveryDeadlineMs = protection.recoveryDeadlineMs()
        val allowed = permitted && !control.currentNeedsBuffer && !control.starving
        val nextReady = permitted && !control.currentNeedsBuffer
        if (speculativeAllowed == allowed && continuityReady == nextReady) return
        speculativeAllowed = allowed
        continuityReady = nextReady
        // Publish after the ownership transaction, outside RecyclerView's selection callback.
        speculationNotification?.cancel()
        speculationNotification = scope.launch {
            yield()
            speculationListener?.accept(speculativeAllowed)
            manager?.invalidate()
            flushWaitingSources()
        }
    }
    private fun ensureHealthCheck() {
        if (!permitted) { healthCheck?.cancel(); healthCheck = null; return }
        if (healthCheck?.isActive == true) return
        healthCheck = scope.launch {
            while (permitted) {
                delay(500)
                selected?.let { url -> leases[url]?.let { observeSelected(url, it.player) } }
            }
        }
    }
    fun attach(url: String, view: PlayerView): ExoPlayer? {
        val lease = leases[url] ?: return null
        if (lease.view !== view) {
            revokeAudio()
            Trace.beginSection("zc.shittok.surface_handoff")
            try { PlayerView.switchTargetView(lease.player, lease.view, view) } finally { Trace.endSection() }
            lease.view = view
            ShitTokPoolDiagnostics.record("surface", url)
        }
        lease.player.volume = if (canPlay(url) && !muted) 1f else 0f
        return lease.player
    }
    fun detach(url: String, view: PlayerView) {
        val lease = leases[url] ?: return
        if (lease.view !== view) return
        silence(lease.player)
        view.player = null
        lease.view = null
    }
    fun source(url: String, stream: CrazyShitRepository.StreamInfo, token: Int): Boolean {
        val lease = leases[url]
        if (lease == null || !accepts(url, token) || !permitted) {
            ShitTokPoolDiagnostics.record("staleSource", url); return false
        }
        if (lease.stream?.mediaUrl == stream.mediaUrl) { syncAudio(); return true }
        if (url != selected && !allowsSpeculativeWork() && !allowsContinuityPreparation(url)) {
            waitingSources[url] = stream to token
            ShitTokPoolDiagnostics.record("neighborDeferred", url)
            return true
        }
        waitingSources.remove(url)
        // Warming a silent next lease must not pause/restart the recovering current player.
        if (url != selected && allowsContinuityPreparation(url)) silence(lease.player) else revokeAudio()
        if (lease.stream != null) runCatching { remember(url, lease.player.currentPosition) }
        removeSource(url)
        val mediaSource = ShitTokMediaSources.create(app, url, stream)
        sources[url] = mediaSource
        lease.stream = stream
        lease.player.setMediaSource(mediaSource, positions[url] ?: 0L)
        ShitTokPoolDiagnostics.record("prepare", "url=$url role=${if(url==selected) "current" else "neighbor"}")
        if (url == selected && !firstPrepare) {
            firstPrepare = true
            AppPerformance.startup("prepare")
            AppPerformance.record("ShitTok first player prepare", SystemClock.elapsedRealtime() - initializedAt)
        }
        lease.player.prepare()
        callbacks.onLeaseChanged(url)
        syncAudio()
        return true
    }
    private fun flushWaitingSources() {
        waitingSources.toMap().forEach { (url, value) ->
            if (url != selected && !allowsSpeculativeWork() && !allowsContinuityPreparation(url)) return@forEach
            waitingSources.remove(url)
            if (accepts(url, value.second)) source(url, value.first, value.second)
        }
    }
    /** N +/- 2 only gets resolved links. No extra player, decoder or speculative full file. */
    fun invalidateSource(url: String) {
        leases[url]?.let {
            runCatching { remember(url, it.player.currentPosition) }
            silence(it.player); it.player.stop(); it.player.clearMediaItems(); it.stream = null
        }
        removeSource(url)
    }
    fun remember(url: String, position: Long) {
        positions.remove(url); positions[url] = position.coerceAtLeast(0)
        while (positions.size > 32) positions.remove(positions.keys.first())
    }
    fun recoverDecoder(player: ExoPlayer, error: PlaybackException): Boolean {
        if (recoveryHandled[player] === error) return true
        if (error.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED || preparedLimit <= 1 || leases.size <= 1) return false
        val failed = leases.entries.firstOrNull { it.value.player === player } ?: return false
        if (failed.value.player.playerError == null) return false
        if (!permitted) { waitingErrors[player] = error; return true }
        revokeAudio()
        recoveryHandled[player] = error
        preparedLimit = minOf(preparedLimit, leases.size) - 1
        val keep = window.take(preparedLimit)
        leases.keys.toList().filter { it !in keep }.forEach { retire(it) }
        window = keep
        ShitTokPoolDiagnostics.record("decoderRecovery", "url=${failed.key} preparedLimit=$preparedLimit")
        if (failed.key in leases) {
            failed.value.player.stop()
            failed.value.player.prepare()
        }
        syncAudio()
        return true
    }
    private fun retire(url: String) {
        val lease = leases[url] ?: return
        silence(lease.player)
        runCatching { remember(url, lease.player.currentPosition) }
        callbacks.onLeaseRetired(url)
        lease.view?.player = null
        listeners.remove(lease.player)?.let { lease.player.removeListener(it) }
        recoveryHandled.remove(lease.player); waitingErrors.remove(lease.player)
        lease.player.stop(); lease.player.clearMediaItems()
        leases.remove(url)
        waitingSources.remove(url)
        pool?.yield(lease.player)
        ShitTokPoolDiagnostics.record("yield", url)
    }
    private fun removeSource(url: String) { sources.remove(url)?.let { manager?.remove(it.mediaItem) } }
    fun release() {
        permission(false, muted)
        healthCheck?.cancel(); healthCheck = null
        speculationNotification?.cancel(); speculationNotification = null
        acquisition?.cancel(); epoch++
        leases.keys.toList().forEach { retire(it) }
        pool?.release(); pool = null
        manager?.release(); manager = null; builder = null; sources.clear(); waitingSources.clear()
        window = emptyList()
        immediateNext = null
        control.continuityId = null
        ShitTokPoolDiagnostics.record("release", "leases=0")
    }
    fun clearSession() { release(); positions.clear(); paused.clear(); selected = null; protection.select("") }
    fun close() { release(); scope.cancel() }
    fun summary(): String = "capacity=3 preparedLimit=$preparedLimit created=$created acquired=${leases.size} " +
        "selected=$selected assignments=${leases.keys} plans=$planCount audible=${leases.values.count { it.player.playWhenReady && it.player.volume>0f }} " +
        leases.entries.joinToString { "${it.key}:buffer=${it.value.player.totalBufferedDuration}" } +
        "\n" + protection.summary(SystemClock.elapsedRealtime()) +
        " speculativeWarming=$speculativeAllowed warmingSuppressedByStarvation=${protection.isProtected()}" +
        " bandwidthEstimate=$bandwidthEstimate" + selected?.let { url ->
            leases[url]?.stream?.let { " pageHost=${Uri.parse(it.pageUrl).host} mediaHost=${Uri.parse(it.mediaUrl).host}" +
                " format=${if(it.mediaUrl.contains(".m3u8", true) || it.mediaUrl.contains(".mpd", true)) "adaptive" else "direct"}" }
        }.orEmpty()

    private fun silence(player: ExoPlayer) {
        endSpeedBoost(player)
        if (player.volume != 0f) player.volume = 0f
        if (player.playWhenReady) player.pause()
    }

    /** Shared playback looper and allocator; role follows promotion instead of fixed creation policy. */
    private class PoolLoadControl : DefaultLoadControl(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
        DEFAULT_MIN_BUFFER_MS, DEFAULT_MIN_BUFFER_MS, DEFAULT_MAX_BUFFER_MS, DEFAULT_MAX_BUFFER_MS,
        DEFAULT_BUFFER_FOR_PLAYBACK_MS, DEFAULT_BUFFER_FOR_PLAYBACK_MS,
        DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS, DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        DEFAULT_TARGET_BUFFER_BYTES, true, true, 0, false) {
        @Volatile var currentId: String? = null
        @Volatile var continuityId: String? = null
        @Volatile var networkPaused = false
        @Volatile var currentNeedsBuffer = true
        @Volatile var starving = false
        @Volatile var recoveryTargetUs = 0L
        @Volatile var recoveryDeadlineMs = 0L
        private val loading = ConcurrentHashMap<PlayerId, Boolean>()
        override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
            val normal = super.shouldContinueLoading(parameters)
            if (networkPaused) return false
            val id = if (parameters.timeline.isEmpty) null else parameters.timeline.getWindow(0, Timeline.Window()).mediaItem.mediaId
            if (id == currentId) return normal
            if (currentNeedsBuffer) return false
            // A READY recovering current retains its recovery target. Give only the immediate
            // next clip a short startup buffer; every other neighbor remains blocked.
            if (starving) return id == continuityId && parameters.bufferedDurationUs < 750_000L
            val buffered = parameters.bufferedDurationUs
            val next = if (loading[parameters.playerId] == true) buffered < 6_000_000 else buffered < 2_500_000
            loading[parameters.playerId] = next
            return next
        }
        override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
            val normal = super.shouldStartPlayback(parameters)
            val id = if (parameters.timeline.isEmpty) null else parameters.timeline.getWindow(0, Timeline.Window()).mediaItem.mediaId
            if (id != currentId || !parameters.rebuffering) return normal
            // Supported LoadControl hook, no player rebuild, pause toggle, or position reset.
            val target = if (parameters.targetLiveOffsetUs != C.TIME_UNSET)
                minOf(recoveryTargetUs, parameters.targetLiveOffsetUs / 2) else recoveryTargetUs
            return ShitTokBufferProtection.mayResume(normal, parameters.bufferedDurationUs,
                parameters.playbackSpeed, target, recoveryDeadlineMs, SystemClock.elapsedRealtime())
        }
        override fun onStopped(playerId: PlayerId) { super.onStopped(playerId); loading.remove(playerId) }
        override fun onReleased(playerId: PlayerId) { super.onReleased(playerId); loading.remove(playerId) }
    }
}
