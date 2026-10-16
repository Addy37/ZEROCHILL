package com.webapp.crazyshit

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.webapp.crazyshit.glass.*
import com.webapp.crazyshit.glass.backdrop.Backdrop
import com.webapp.crazyshit.glass.backdrop.backdrops.emptyBackdrop
import com.webapp.crazyshit.glass.backdrop.backdrops.layerBackdrop
import com.webapp.crazyshit.glass.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

/** Only the navbar is Compose. Existing Activity, pager, menus and data remain Views. */
internal class SquareNavigationBridge(private val nav: ZeroChillBottomNavigationView) {
    private val activity: Activity = generateSequence(nav.context) {
        (it as? android.content.ContextWrapper)?.baseContext
    }.filterIsInstance<Activity>().first()
    private var page by mutableStateOf<Backdrop>(emptyBackdrop())
    private val position = mutableFloatStateOf(0f)
    private val committedIndex = mutableIntStateOf(0)
    private val collapse = mutableFloatStateOf(0f)
    private val unread = mutableStateOf(false)
    private var owner: NavOwner? = null
    private var view: ComposeView? = null

    fun attach() {
        if (view != null) return
        val lifecycle = NavOwner(activity)
        owner = lifecycle
        // WindowRecomposer is installed on the Activity content root, above ComposeView.
        // The legacy Activity has no AndroidX owners, so expose the same local owner
        // there as well. No Activity superclass or Back behavior changes.
        activity.window.decorView.setViewTreeLifecycleOwner(lifecycle)
        activity.window.decorView.setViewTreeSavedStateRegistryOwner(lifecycle)
        view = ComposeView(activity).also { compose ->
            compose.setViewTreeLifecycleOwner(lifecycle)
            compose.setViewTreeSavedStateRegistryOwner(lifecycle)
            compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            compose.clipChildren = false
            compose.clipToPadding = false
            compose.setContent {
                MaterialTheme(colorScheme = darkColorScheme(primary = SelectedInk)) { Navigation() }
            }
            nav.addView(compose, FrameLayout.LayoutParams(-1, -1))
        }
    }

    fun update(backdrop: ViewPageBackdrop?, pagerPosition: Float, folded: Float, hasUnread: Boolean) {
        page = backdrop ?: emptyBackdrop()
        position.floatValue = pagerPosition
        if (!nav.isSquareDragging() && kotlin.math.abs(pagerPosition - pagerPosition.roundToInt()) < .001f) {
            committedIndex.intValue = pagerPosition.roundToInt().coerceIn(0, 3)
        }
        collapse.floatValue = folded
        unread.value = hasUnread
    }
    fun dispatchInsets() { view?.setPadding(0, 0, 0, 0) }
    fun detach() {
        view?.let { it.disposeComposition(); nav.removeView(it) }
        view = null
        owner?.close()
        owner = null
    }

    @Composable private fun Navigation() {
        val selectedIndex = remember { { committedIndex.intValue } }
        val externalPosition = remember { { position.floatValue } }
        val shell = rememberLayerBackdrop()
        val config = remember {
            if (BuildConfig.DEBUG && activity.intent.getBooleanExtra("probe_no_lens", false))
                GlassEffectConfig(lensHeight = 0f, lensAmount = 0f)
            else GlassEffectConfig()
        }
        val shape = remember { RoundedCornerShape(50) }
        val height = (61f - 11f * collapse.floatValue).dp
        val film = Color.White.copy(alpha = .12f)
        CompositionLocalProvider(
            LocalAppBackdrop provides page,
            LocalGlassEffectConfig provides config,
            LocalLiquidBottomTabsSurfaceBackdrop provides shell,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Square's GeometricFloatingTabBar captures the actual shell before its
                // selector siblings. Keep that ordering, without its player/search slots.
                Box(Modifier.fillMaxSize().layerBackdrop(shell)
                    .background(film, shape).clip(shape)
                    .liquidGlass(config, shape, highlightAlpha = .3f))
                LiquidBottomTabs(
                    selectedTabIndex = selectedIndex,
                    onTabSelected = nav::selectSquareTab,
                    backdrop = page,
                    tabsCount = 4,
                    accentColor = SelectedInk,
                    containerColor = film,
                    indicatorColor = Color.Black.copy(alpha = .5f),
                    height = height,
                    lensDepth = 24.dp,
                    drawContainer = false,
                    pagerPosition = externalPosition,
                    isNavigationDragging = nav::isSquareDragging,
                    onNavigationDragStart = nav::startSquareDrag,
                    onNavigationDragBy = nav::dragSquareBy,
                    onNavigationDragEnd = nav::endSquareDrag,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val replica = LocalLiquidBottomTabsReplica.current
                    for (index in 0..3) {
                        val ink = if (replica || selectedIndex() == index) SelectedInk else InactiveInk
                        LiquidBottomTab(
                            onClick = { nav.selectSquareTab(index) },
                            modifier = Modifier.semantics { selected = selectedIndex() == index },
                        ) {
                            Box {
                                Icon(painterResource(Icons[index]), Labels[index], tint = ink,
                                    modifier = Modifier.size(26.dp))
                                if (index == 3 && unread.value) Box(Modifier.align(Alignment.TopEnd)
                                    .size(5.dp).background(SelectedInk, RoundedCornerShape(50)))
                            }
                            // Preserve ZEROCHILL's four-icon collapse instead of Square's
                            // player/search fold. Expanded typography follows Square.
                            val labelFraction = (1f - collapse.floatValue * 1.45f).coerceIn(0f, 1f)
                            if (labelFraction > 0f) Text(Labels[index], color = ink,
                                fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold,
                                style = LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                maxLines = 1,
                                modifier = Modifier.height((11f * labelFraction).dp)
                                    .graphicsLayer { alpha = labelFraction })
                        }
                    }
                }
            }
        }
    }
    companion object {
        const val SELECTED_ARGB = 0xFF0892D0.toInt()
        const val INACTIVE_ARGB = 0xFFB8C0CC.toInt()
        val SelectedInk = Color(SELECTED_ARGB)
        val InactiveInk = Color(INACTIVE_ARGB)
        val Labels = arrayOf("Shows", "ShitTok", "OnlyFap", "Library")
        val Icons = intArrayOf(R.drawable.ic_nav_series, R.drawable.ic_nav_chaos,
            R.drawable.ic_nav_onlyfap, R.drawable.ic_more_library)
    }
}

/** A navbar-local lifecycle for the existing android.app.Activity host. */
private class NavOwner(private val activity: Activity) : SavedStateRegistryOwner,
    Application.ActivityLifecycleCallbacks {
    override val lifecycle = LifecycleRegistry(this)
    private val state = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = state.savedStateRegistry
    init {
        state.performAttach()
        state.performRestore(null)
        lifecycle.currentState = Lifecycle.State.RESUMED
        activity.application.registerActivityLifecycleCallbacks(this)
    }
    override fun onActivityStarted(a: Activity) { if (a === activity) lifecycle.currentState = Lifecycle.State.STARTED }
    override fun onActivityResumed(a: Activity) { if (a === activity) lifecycle.currentState = Lifecycle.State.RESUMED }
    override fun onActivityPaused(a: Activity) { if (a === activity) lifecycle.currentState = Lifecycle.State.STARTED }
    override fun onActivityStopped(a: Activity) { if (a === activity) lifecycle.currentState = Lifecycle.State.CREATED }
    override fun onActivityDestroyed(a: Activity) { if (a === activity) close() }
    override fun onActivityCreated(a: Activity, saved: Bundle?) {}
    override fun onActivitySaveInstanceState(a: Activity, out: Bundle) {}
    fun close() {
        activity.application.unregisterActivityLifecycleCallbacks(this)
        lifecycle.currentState = Lifecycle.State.DESTROYED
    }
}
