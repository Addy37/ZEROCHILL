package com.webapp.crazyshit

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.graphics.withSave
import androidx.annotation.RequiresApi
import com.webapp.crazyshit.glass.backdrop.internal.RoundedRectRefractionShaderString

/** Native controls over the same recorded page and lens shader used by navigation/search. */
// Programmatic controls are required; this pane is never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
internal class PreferenceGlassPanel @JvmOverloads constructor(context: Context, private val controls: View, backdropSource: View? = null) : FrameLayout(context) {
    private val activity = context as? Activity
    private val source = backdropSource ?: activity?.window?.decorView?.findViewById<View>(android.R.id.content)
    private val sharedPage = findShell(source)?.pageBackdropForOverlay()
    private val fallbackPage = source?.let { ViewPageBackdrop(it) }
    private var fallbackDirty = true
    private var observer: ViewTreeObserver? = null
    private val sourceDraw = ViewTreeObserver.OnDrawListener {
        fallbackDirty = true
        postInvalidateOnAnimation()
    }
    private var renderer: LensRenderer? = null
    private var glassFailed = false
    private var reported = false
    private var reportedCanvas = false
    private var forming: ValueAnimator? = null
    internal var formation = if (ZeroChillMotion.animationsEnabled(context)) 0f else 1f
        private set
    private val shape = Path()
    private val bounds = RectF()
    private val wash = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        tag = "preference_glass_panel"
        addView(controls, LayoutParams(-1, -2))
        controls.alpha = formation
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (usesOpticalLayer(context)) {
            source?.viewTreeObserver?.let { observer = it; it.addOnDrawListener(sourceDraw) }
        }
        if (formation < 1f) post {
            if (!isAttachedToWindow) return@post
            forming = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 320L
                interpolator = android.view.animation.PathInterpolator(.18f, .85f, .25f, 1f)
                addUpdateListener {
                    formation = it.animatedValue as Float
                    controls.alpha = ((formation - .22f) / .78f).coerceIn(0f, 1f)
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        formation = 1f
                        controls.alpha = 1f
                        invalidate()
                        if (BuildConfig.DEBUG) android.util.Log.i("ZeroChillGlass", "preference formation complete")
                    }
                })
                start()
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val insetX = width * .40f * (1f - formation)
        val insetY = height * .44f * (1f - formation)
        bounds.set(insetX, insetY, width - insetX, height - insetY)
        val radius = dp(28f) + (minOf(bounds.width(), bounds.height()) * .5f - dp(28f)) * (1f - formation)
        shape.rewind(); shape.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        canvas.withSave {
            canvas.clipPath(shape)
            var sampled = false
            if (BuildConfig.DEBUG && !reportedCanvas) {
                reportedCanvas = true
                android.util.Log.i("ZeroChillGlass", "preference canvas hardware=" + canvas.isHardwareAccelerated)
            }
            if (!glassFailed && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated &&
                usesOpticalLayer(context)) {
                try {
                    val page = sharedPage?.takeIf { it.recordings > 0 } ?: fallbackPage
                    if (page === fallbackPage && fallbackDirty && source != null && source.width > 0 && source.height > 0) {
                        fallbackDirty = false
                        page?.record(source.width, source.height) { source.draw(it) }
                    }
                    if (page != null && page.recordings > 0) {
                        if (renderer == null) renderer = LensRenderer()
                        renderer!!.draw(canvas, page)
                        sampled = true
                        if (BuildConfig.DEBUG && !reported) {
                            reported = true
                            android.util.Log.i("ZeroChillGlass", "preference renderer=" + if (Build.VERSION.SDK_INT >= 33) "lens" else "blur")
                        }
                    }
                } catch (error: Exception) {
                    glassFailed = true
                    if (BuildConfig.DEBUG) android.util.Log.w("ZeroChillGlass", "preference glass fallback", error)
                }
            }
            // Readability wash is separate from the optical layer and never blurs native ink.
            wash.shader = null
            val tintColor = AppearanceTheme.surface(context)
            val opacity = AppearanceTheme.menuTint(context)
            val transparency = if (AppearanceTheme.hasAdvancedGlass(context))
                AppearanceTheme.glassTransparency(context) else .78f
            // High-contrast dark film keeps text legible over faces, hero artwork,
            // and fast-moving videos, including at the lowest tint setting.
            // Optical sampling remains underneath this film.
            val alpha = if (sampled) (157 + opacity * 50f * (1f - transparency * .25f)).toInt()
                else (212 + opacity * 34f).toInt()
            wash.color = Color.argb(alpha, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
            canvas.drawPath(shape, wash)
            val accent = AppearanceTheme.accent(context)
            wash.shader = LinearGradient(0f, bounds.top, width.toFloat(), bounds.bottom,
                intArrayOf(0x22FFFFFF, 0x03FFFFFF, (accent and 0x00FFFFFF) or 0x18000000), null, Shader.TileMode.CLAMP)
            wash.alpha = (wash.alpha * edgeStrength(context)).toInt().coerceIn(0, 255)
            canvas.drawPath(shape, wash)
            super.dispatchDraw(canvas)
            rim.strokeWidth = dp(1f)
            rim.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                intArrayOf(0xB0FFFFFF.toInt(), 0x18FFFFFF, (accent and 0x00FFFFFF) or 0x50000000, 0x70FFFFFF), null, Shader.TileMode.CLAMP)
            rim.alpha = (255 * edgeStrength(context)).toInt().coerceIn(0, 255)
            canvas.drawPath(shape, rim)
        }
    }

    override fun onDetachedFromWindow() {
        forming?.removeAllUpdateListeners(); forming?.removeAllListeners(); forming?.cancel(); forming = null
        observer?.takeIf { it.isAlive }?.removeOnDrawListener(sourceDraw); observer = null
        if (Build.VERSION.SDK_INT >= 31) renderer?.release()
        renderer = null
        fallbackPage?.release(); fallbackDirty = true
        super.onDetachedFromWindow()
    }

    @RequiresApi(31)
    private inner class LensRenderer {
        private val node = RenderNode("ZEROCHILL preference glass")
        private var effectWidth = 0
        private var effectHeight = 0
        private var effectIntensity = -1f
        private var effectBlur = -1f
        private var effectRefraction = -1f
        private var effectVibrancy = -1f
        fun draw(canvas: Canvas, page: ViewPageBackdrop) {
            val intensity = AppearanceTheme.glassIntensity(context)
            val advanced = AppearanceTheme.hasAdvancedGlass(context)
            val blurValue = if (advanced) AppearanceTheme.glassBlur(context) else 1.4f + intensity * 2f
            val refraction = if (advanced) AppearanceTheme.glassRefraction(context) else 21f
            val vibrancy = if (advanced) AppearanceTheme.glassVibrancy(context) else 1f
            if (effectWidth != width || effectHeight != height || effectIntensity != intensity ||
                effectBlur != blurValue || effectRefraction != refraction || effectVibrancy != vibrancy) {
                var effect: RenderEffect? = if (blurValue > 0f)
                    RenderEffect.createBlurEffect(dp(blurValue), dp(blurValue), Shader.TileMode.CLAMP) else null
                if (vibrancy != 1f) {
                    val matrix = ColorMatrix().apply { setSaturation(vibrancy) }
                    val color = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
                    effect = effect?.let { RenderEffect.createChainEffect(color, it) } ?: color
                }
                if (Build.VERSION.SDK_INT >= 33 && refraction > 0f && intensity > 0f) {
                    val lens = lens()
                    effect = effect?.let { RenderEffect.createChainEffect(lens, it) } ?: lens
                }
                node.setRenderEffect(effect)
                node.setPosition(0, 0, width, height)
                effectWidth = width; effectHeight = height
                effectIntensity = intensity; effectBlur = blurValue
                effectRefraction = refraction; effectVibrancy = vibrancy
            }
            val recording = node.beginRecording(width, height)
            try { page.drawPageIntoDialog(recording, this@PreferenceGlassPanel) }
            finally { node.endRecording() }
            canvas.drawRenderNode(node)
        }
        @RequiresApi(33)
        private fun lens(): RenderEffect {
            // Same shader, edge height, amount and center magnification as navigationGlassConfig.
            val shader = RuntimeShader(RoundedRectRefractionShaderString)
            shader.setFloatUniform("size", width.toFloat(), height.toFloat())
            shader.setFloatUniform("offset", 0f, 0f)
            val radius = dp(28f)
            shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
            val optical = (AppearanceTheme.glassIntensity(context) * 2f).coerceIn(0f, 1.45f)
            val depth = if (AppearanceTheme.hasAdvancedGlass(context))
                AppearanceTheme.glassRefraction(context) * optical else .44f * optical * 48f
            shader.setFloatUniform("refractionHeight", dp(depth.coerceAtMost(30f)))
            shader.setFloatUniform("refractionAmount", -dp((depth * 1.20f).coerceAtMost(38f)))
            shader.setFloatUniform("depthEffect", 0f)
            shader.setFloatUniform("magnification", 1f + .025f * (AppearanceTheme.glassIntensity(context) * 2f))
            shader.setFloatUniform("sideEdges", 1f)
            return RenderEffect.createRuntimeShaderEffect(shader, "content")
        }
        fun release() = node.discardDisplayList()
    }

    companion object {
        @JvmStatic internal fun usesOpticalLayer(context: Context): Boolean {
            if (!AppearanceTheme.hasAdvancedGlass(context)) return true
            return when (AppearanceTheme.glassPreset(context)) {
                AppearanceTheme.GlassPreset.OFF, AppearanceTheme.GlassPreset.FAST -> false
                else -> true
            }
        }

        @JvmStatic internal fun edgeStrength(context: Context): Float =
            if (AppearanceTheme.hasAdvancedGlass(context))
                (AppearanceTheme.glassEdge(context) / .7f).coerceIn(0f, 1.5f) else 1f
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun findShell(view: View?): FrostedNavigationLayout? {
        if (view is FrostedNavigationLayout) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findShell(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
