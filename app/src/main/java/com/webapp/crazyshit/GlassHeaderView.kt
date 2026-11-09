package com.webapp.crazyshit

import android.app.Activity
import android.content.SharedPreferences
import android.widget.FrameLayout
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    private val resultsSurface: Boolean = false,
    private val searchControlSurface: Boolean = false,
    private val navigationSurface: Boolean = false,
    private val surfaceInset: Float = 0f
) : FrameLayout(activity) {
    private val glass = ComposeView(activity).apply {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }
    private var backdrop by mutableStateOf<ViewPageBackdrop?>(null)
    private var recentMaterial by mutableStateOf(false)
    private var appearanceRevision by mutableIntStateOf(0)
    private val preferences = AppearanceTheme.prefs(activity)
    private val appearanceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppearanceTheme.KEY_MODE || key == AppearanceTheme.KEY_ACCENT ||
            key?.startsWith("appearance_glass_") == true) {
            post { if (isAttachedToWindow) appearanceRevision++ }
        }
    }

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
    fun setRecentSurface(recent: Boolean) { recentMaterial = recent }
    fun sharesBackdropWith(other: GlassHeaderView): Boolean = backdrop === other.backdrop
    fun usesHeaderGlass(): Boolean = !resultsSurface

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(appearanceListener)
        // Navbar installs the legacy Activity's AndroidX owners during this attach pass.
        post {
            if (!isAttachedToWindow) return@post
            glass.setContent {
                val config = remember(recentMaterial, appearanceRevision) { (if (resultsSurface && recentMaterial) recentSearchGlassConfig(activity)
                    else if (resultsSurface) searchResultsGlassConfig(activity)
                    else if (searchControlSurface) searchControlsGlassConfig(activity)
                    else if (navigationSurface) navigationGlassConfig(activity)
                    else headerGlassConfig(activity)).let {
                    if (cornerRadius > 0f) it.copy(lensSideEdges = true) else it
                } }
                val shape = remember { RoundedCornerShape(cornerRadius.dp) }
                backdrop?.let { page ->
                    CompositionLocalProvider(LocalAppBackdrop provides page,
                        LocalGlassEffectConfig provides config) {
                        Box(Modifier.fillMaxSize().padding(surfaceInset.dp).clipToBounds()
                            // Floating capsules share the navbar rim; full-width headers keep none.
                            .liquidGlass(config, shape, highlightAlpha = if (cornerRadius > 0f) glassRimAlpha(activity) else 0f))
                    }
                }
            }
        }
    }
    override fun onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(appearanceListener)
        super.onDetachedFromWindow()
    }
}

/** Shared header material follows the selected theme. */
internal fun headerGlassConfig(activity: Activity): GlassEffectConfig =
    navigationGlassConfig(activity).copy(
        surfaceTintColor = if (AppearanceTheme.isLight(activity)) Color.White else Color.Black,
        lensSideEdges = false,
    )

/** Search keeps the navbar lens and rim, with a little local diffusion for native text.
 * Both states deliberately use one material; geometry and recording stay native-owned.
 */
internal fun searchResultsGlassConfig(activity: Activity): GlassEffectConfig =
    navigationGlassConfig(activity).let { base ->
        base.copy(
            surfaceTintColor = if (AppearanceTheme.isLight(activity)) Color.White else Color.Black,
            blurRadius = (base.blurRadius * 1.6f).coerceAtMost(2f),
            surfaceOpacity = (base.surfaceOpacity + .06f).coerceAtMost(.30f),
        )
    }

/** Search capsules add a local black film without changing shared header/navbar material. */
internal fun searchControlsGlassConfig(activity: Activity): GlassEffectConfig =
    headerGlassConfig(activity).let { it.copy(surfaceOpacity = (it.surfaceOpacity + .04f).coerceAtMost(.27f)) }

internal fun recentSearchGlassConfig(activity: Activity): GlassEffectConfig =
    searchResultsGlassConfig(activity)
