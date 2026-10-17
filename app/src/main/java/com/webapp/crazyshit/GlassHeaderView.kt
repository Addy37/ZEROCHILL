package com.webapp.crazyshit

import android.app.Activity
import android.widget.FrameLayout
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.webapp.crazyshit.glass.*

/** Native controls stay above a single pane sampling the navbar's live page recording. */
internal class GlassHeaderView @JvmOverloads constructor(
    private val activity: Activity, private val controls: View, private val cornerRadius: Float = 0f,
    private val resultsSurface: Boolean = false
) : FrameLayout(activity) {
    private val glass = ComposeView(activity).apply {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }
    private var backdrop by mutableStateOf<ViewPageBackdrop?>(null)

    init {
        if (cornerRadius > 0f) {
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height,
                        cornerRadius * resources.displayMetrics.density)
                }
            }
            clipToOutline = true
        }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        contentDescription = controls.contentDescription
        controls.contentDescription = null
        addView(glass, LayoutParams(-1, -1))
        controls.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(controls, LayoutParams(-1, -1))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.EXACTLY) {
            // Search wraps its native controls; the fill-sized Compose pane must not
            // choose the height of the whole available window.
            controls.measure(widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                controls.measuredHeight, View.MeasureSpec.EXACTLY))
        } else super.onMeasure(widthMeasureSpec, heightMeasureSpec)
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
                val config = remember { (if (resultsSurface) searchResultsGlassConfig(activity)
                    else headerGlassConfig(activity)).let {
                    if (cornerRadius > 0f) it.copy(lensSideEdges = true) else it
                } }
                val shape = remember { RoundedCornerShape(cornerRadius.dp) }
                backdrop?.let { page ->
                    CompositionLocalProvider(LocalAppBackdrop provides page,
                        LocalGlassEffectConfig provides config) {
                        Box(Modifier.fillMaxSize().clipToBounds()
                            // Floating capsules share the navbar rim; full-width headers keep none.
                            .liquidGlass(config, shape, highlightAlpha = if (cornerRadius > 0f) .3f else 0f))
                    }
                }
            }
        }
    }
}

/** Black adds no light on an empty OLED header; only the live backdrop reveals the pane. */
internal fun headerGlassConfig(activity: Activity): GlassEffectConfig =
    navigationGlassConfig(activity).copy(surfaceTintColor = Color.Black, lensSideEdges = false)

/** Soften busy page artwork behind result text without adding another blur layer. */
internal fun searchResultsGlassConfig(activity: Activity): GlassEffectConfig =
    headerGlassConfig(activity).copy(blurRadius = 12f, surfaceOpacity = .40f)
