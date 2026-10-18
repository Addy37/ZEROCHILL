package com.webapp.crazyshit.glass.backdrop

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import com.webapp.crazyshit.glass.backdrop.effects.lens
import com.webapp.crazyshit.glass.backdrop.internal.RoundedRectRefractionShaderString
import com.webapp.crazyshit.glass.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.annotation.GraphicsMode

/** Native Skia verifies the real AGSL mapping, not hardware/device appearance. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClearGlassLensTest {
    @Test fun centerMagnifiesLiveInputInBothShaderVariants() {
        for (source in listOf(RoundedRectRefractionShaderString,
            RoundedRectRefractionWithDispersionShaderString)) {
            val input = RuntimeShader("""
                uniform float edge;
                half4 main(float2 p) {
                    return half4(half3(step(edge, p.x)), 1.0);
                }
            """)
            input.setFloatUniform("edge", 72f)
            val shader = RuntimeShader(source)
            shader.setFloatUniform("size", 120f, 60f)
            shader.setFloatUniform("offset", 0f, 0f)
            shader.setFloatUniform("cornerRadii", 30f, 30f, 30f, 30f)
            shader.setFloatUniform("refractionHeight", 8f)
            shader.setFloatUniform("refractionAmount", -12f)
            shader.setFloatUniform("depthEffect", 0f)
            if (source == RoundedRectRefractionWithDispersionShaderString) {
                shader.setFloatUniform("chromaticAberration", 1f)
            }
            shader.setInputShader("content", input)
            shader.setFloatUniform("magnification", 1f)
            assertEquals("Original center mapping", Color.WHITE, pixel(shader, 72, 30))
            shader.setFloatUniform("magnification", 1.08f)
            assertEquals("Local outward magnification", Color.BLACK, pixel(shader, 72, 30))
            // Change the input, not the lens: it must continue sampling live content.
            input.setFloatUniform("edge", 70f)
            shader.setInputShader("content", input)
            assertEquals("Changed live input", Color.WHITE, pixel(shader, 72, 30))
        }
    }

    @Test fun lensUpdatesUseTheSameCachedShader() {
        val scope = object : BackdropEffectScopeImpl() {
            override val shape = RoundedCornerShape(50)
        }
        scope.size = Size(120f, 60f)
        scope.apply { lens(8f, 12f, magnification = 1.08f) }
        val cached = scope.obtainRuntimeShader("Refraction", RoundedRectRefractionShaderString)
        assertNotNull(scope.renderEffect)
        scope.apply { lens(11f, 16f, magnification = 1.12f) }
        assertSame(cached, scope.obtainRuntimeShader("Refraction", RoundedRectRefractionShaderString))
        assertNotNull(scope.renderEffect)
    }

    @Test @Config(sdk = [28, 31, 32])
    fun unsupportedPlatformsRetainNonRefractivePath() {
        val scope = object : BackdropEffectScopeImpl() {
            override val shape = RoundedCornerShape(50)
        }
        scope.size = Size(120f, 60f)
        scope.apply { lens(8f, 12f, magnification = 1.08f) }
        assertNull(scope.renderEffect)
    }

    private fun pixel(shader: RuntimeShader, x: Int, y: Int): Int {
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            // Test-only: let native Skia evaluate AGSL on the CPU. Android's
            // Canvas guard otherwise rejects RuntimeShader before reaching it.
            // This verifies sampling math, not GPU/device rendering support.
            ReflectionHelpers.setField(canvas, "mAllowHwFeaturesInSwMode", true)
            canvas.drawPaint(Paint().apply { this.shader = shader })
            bitmap.getPixel(x, y)
        } finally { bitmap.recycle() }
    }
}
