package com.webapp.crazyshit

import android.app.Activity
import android.widget.FrameLayout
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.webapp.crazyshit.glass.*

/** Native controls stay above a single pane sampling the navbar's live page recording. */
internal class GlassHeaderView(private val activity: Activity, controls: View) : FrameLayout(activity) {
    private val glass = ComposeView(activity).apply {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }
    private var backdrop: ViewPageBackdrop? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(glass, LayoutParams(-1, -1))
        controls.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(controls, LayoutParams(-1, -1))
    }

    fun setGlassEnabled(enabled: Boolean) {
        glass.visibility = if (enabled) VISIBLE else GONE
        setBackgroundColor(if (enabled) android.graphics.Color.TRANSPARENT else ZeroChillUi.background(activity))
    }

    fun setPageBackdrop(page: ViewPageBackdrop) { backdrop = page }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Navbar installs the legacy Activity's AndroidX owners during this attach pass.
        post {
            if (!isAttachedToWindow) return@post
            glass.setContent {
                val config = remember { navigationGlassConfig(activity) }
                val shape = remember { RoundedCornerShape(0.dp) }
                backdrop?.let { page ->
                    CompositionLocalProvider(LocalAppBackdrop provides page,
                        LocalGlassEffectConfig provides config) {
                        Box(Modifier.fillMaxSize()
                            .background(Color.White.copy(alpha = .04f), shape)
                            // Full-width geometry has no illuminated floating-card rim.
                            .liquidGlass(config, shape, highlightAlpha = 0f))
                    }
                }
            }
        }
    }
}
