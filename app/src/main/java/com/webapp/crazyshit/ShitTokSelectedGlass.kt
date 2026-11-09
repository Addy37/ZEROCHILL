package com.webapp.crazyshit

import android.app.Activity
import android.content.SharedPreferences
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.webapp.crazyshit.glass.*

/** The navbar's actual selected lens, behind the existing accessible native labels. */
internal class ShitTokSelectedGlass(private val activity: Activity, page: ViewPageBackdrop) : FrameLayout(activity) {
    private var selection by mutableIntStateOf(0)
    private var appearanceRevision by mutableIntStateOf(0)
    private val prefs = AppearanceTheme.prefs(activity)
    private val appearanceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppearanceTheme.KEY_ACCENT || key?.startsWith("appearance_glass_") == true) {
            post { if (isAttachedToWindow) appearanceRevision++ }
        }
    }
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            // Match GlassHeaderView: navbar installs AndroidX owners during attach.
            post { if (isAttachedToWindow) setContent {
                val config = remember(appearanceRevision) { navigationGlassConfig(activity) }
                CompositionLocalProvider(LocalGlassEffectConfig provides config) {
                    LiquidBottomTabs(selectedTabIndex = { selection }, onTabSelected = {},
                        backdrop = page, tabsCount = 2, height = 36.dp, lensDepth = 16.dp,
                        accentColor = Color(AppearanceTheme.accent(activity)),
                        indicatorColor = Color.Black.copy(alpha = .10f), drawContainer = false) {
                        repeat(2) { Spacer(Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            } }
        }, LayoutParams(-1, -1))
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(appearanceListener)
    }
    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(appearanceListener)
        super.onDetachedFromWindow()
    }
    fun select(index: Int) { selection = index }
}
