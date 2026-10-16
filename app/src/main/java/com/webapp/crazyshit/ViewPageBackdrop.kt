package com.webapp.crazyshit

import android.graphics.Canvas
import android.graphics.RenderNode
import android.os.Build
import android.view.View
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.webapp.crazyshit.glass.backdrop.Backdrop
import com.webapp.crazyshit.glass.backdrop.internal.InverseLayerScope

/** The real View subtree, excluding navigation. No screenshots or bitmap readback. */
class ViewPageBackdrop(private val source: View) : Backdrop {
    private val node = if (Build.VERSION.SDK_INT >= 29) RenderNode("ZEROCHILL page backdrop") else null
    private val location = IntArray(2)
    private val inverse = InverseLayerScope()
    private val revision = mutableIntStateOf(0)
    private var recorded = false
    var recordings = 0
        private set

    fun record(width: Int, height: Int, draw: (Canvas) -> Unit) {
        val node = node ?: return
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording(width, height)
        try { draw(canvas) } finally { node.endRecording() }
        recorded = true
        recordings++
        // Only a changed page advances this. Navbar springs never drive page capture.
        revision.intValue++
    }

    fun drawPage(canvas: Canvas): Boolean {
        if (!canvas.isHardwareAccelerated || !recorded) return false
        node?.let { canvas.drawRenderNode(it) }
        return true
    }

    override val isCoordinatesDependent = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        revision.intValue // Draw dependency, never a recomposition or self-invalidating clock.
        val coordinates = coordinates ?: return
        val node = node ?: return
        if (!recorded || !drawContext.canvas.nativeCanvas.isHardwareAccelerated) return
        source.getLocationInWindow(location)
        val position = coordinates.positionInWindow()
        withTransform({
            if (layerBlock != null) with(inverse.apply { reset() }) {
                inverseTransform(density, layerBlock)
            }
            translate(location[0] - position.x, location[1] - position.y)
        }) { drawContext.canvas.nativeCanvas.drawRenderNode(node) }
    }

    fun release() {
        node?.discardDisplayList()
        recorded = false
    }
}
