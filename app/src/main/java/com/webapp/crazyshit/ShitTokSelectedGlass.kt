package com.webapp.crazyshit

import android.app.Activity
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
internal class ShitTokSelectedGlass(activity: Activity, page: ViewPageBackdrop) : FrameLayout(activity) {
    private var selection by mutableIntStateOf(0)
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            // Match GlassHeaderView: navbar installs AndroidX owners during attach.
            post { if (isAttachedToWindow) setContent {
                val config = remember { navigationGlassConfig(activity) }
                CompositionLocalProvider(LocalGlassEffectConfig provides config) {
                    LiquidBottomTabs(selectedTabIndex = { selection }, onTabSelected = {},
                        backdrop = page, tabsCount = 2, height = 36.dp, lensDepth = 16.dp,
                        accentColor = Color(SquareNavigationBridge.SELECTED_ARGB),
                        indicatorColor = Color.Black.copy(alpha = .10f), drawContainer = false) {
                        repeat(2) { Spacer(Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            } }
        }, LayoutParams(-1, -1))
    }
    fun select(index: Int) { selection = index }
}
