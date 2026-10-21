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
import androidx.annotation.RequiresApi
import com.webapp.crazyshit.glass.backdrop.internal.RoundedRectRefractionShaderString

/** Native controls over the same recorded page and lens shader used by navigation/search. */
internal class PreferenceGlassPanel(context: Context, private val controls: View) : FrameLayout(context) {
    private val activity = context as? Activity
    private val source = activity?.window?.decorView?.findViewById<View>(android.R.id.content)
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
        source?.viewTreeObserver?.let { observer = it; it.addOnDrawListener(sourceDraw) }
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
        val checkpoint = canvas.save()
        canvas.clipPath(shape)
        var sampled = false
        if (!glassFailed && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) {
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
        wash.color = if (sampled) 0x58030910 else 0xEB0A121B.toInt()
        canvas.drawPath(shape, wash)
        wash.shader = LinearGradient(0f, bounds.top, width.toFloat(), bounds.bottom,
            intArrayOf(0x22FFFFFF, 0x03FFFFFF, 0x0C0892D0), null, Shader.TileMode.CLAMP)
        canvas.drawPath(shape, wash)
        super.dispatchDraw(canvas)
        rim.strokeWidth = dp(1f)
        rim.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
            intArrayOf(0xB0FFFFFF.toInt(), 0x18FFFFFF, 0x500892D0, 0x70FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawPath(shape, rim)
        canvas.restoreToCount(checkpoint)
    }

    override fun onDetachedFromWindow() {
        forming?.removeAllUpdateListeners(); forming?.cancel(); forming = null
        observer?.takeIf { it.isAlive }?.removeOnDrawListener(sourceDraw); observer = null
        renderer?.release(); renderer = null
        fallbackPage?.release(); fallbackDirty = true
        super.onDetachedFromWindow()
    }

    @RequiresApi(31)
    private inner class LensRenderer {
        private val node = RenderNode("ZEROCHILL preference glass")
        private var effectWidth = 0
        private var effectHeight = 0
        fun draw(canvas: Canvas, page: ViewPageBackdrop) {
            if (effectWidth != width || effectHeight != height) {
                val blur = RenderEffect.createBlurEffect(dp(3f), dp(3f), Shader.TileMode.CLAMP)
                val effect = if (Build.VERSION.SDK_INT >= 33) lens(blur) else blur
                node.setRenderEffect(effect)
                node.setPosition(0, 0, width, height)
                effectWidth = width; effectHeight = height
            }
            val recording = node.beginRecording(width, height)
            try { page.drawPageIntoDialog(recording, this@PreferenceGlassPanel) }
            finally { node.endRecording() }
            canvas.drawRenderNode(node)
        }
        @RequiresApi(33)
        private fun lens(blur: RenderEffect): RenderEffect {
            // Same shader, edge height, amount and center magnification as navigationGlassConfig.
            val shader = RuntimeShader(RoundedRectRefractionShaderString)
            shader.setFloatUniform("size", width.toFloat(), height.toFloat())
            shader.setFloatUniform("offset", 0f, 0f)
            val radius = dp(28f)
            shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
            shader.setFloatUniform("refractionHeight", dp(.44f * 48f))
            shader.setFloatUniform("refractionAmount", -dp(.6f * 48f))
            shader.setFloatUniform("depthEffect", 0f)
            shader.setFloatUniform("magnification", 1.025f)
            shader.setFloatUniform("sideEdges", 1f)
            return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"), blur)
        }
        fun release() = node.discardDisplayList()
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
