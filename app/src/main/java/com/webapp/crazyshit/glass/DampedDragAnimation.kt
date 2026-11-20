// Vendored from the Backdrop catalog app, Apache-2.0.
//
//   https://github.com/Kyant0/AndroidLiquidGlass
//   commit b18eb0ff12c616546a68c72e7d0097f1ab286c87
//
// These are the library author's own example components rather than part of the
// published artifact, so there is nothing to depend on — they have to be copied.
// Kept as close to upstream as possible (package line and a few Material
// swaps aside) so a later upstream fix can be diffed in; see LICENSE-backdrop.txt.

package com.webapp.crazyshit.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    val onDragCanceled: DampedDragAnimation.() -> Unit = onDragStopped,
    val motionEnabled: () -> Boolean = { true },
) {

    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val pressProgressAnimationSpec =
        spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec =
        spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.7f, 250f, 0.001f)

    private val valueAnimation =
        Animatable(initialValue, visibilityThreshold)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private var pressJob: Job? = null

    val value: Float get() = valueAnimation.value
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                if (motionEnabled()) press()
            },
            onDragEnd = {
                onDragStopped()
                if (motionEnabled()) release()
            },
            onDragCancel = {
                onDragCanceled()
                if (motionEnabled()) release()
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    private fun press() = animatePress(true)

    private fun release() = animatePress(false)

    private fun animatePress(pressed: Boolean) {
        pressJob?.cancel()
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(if (pressed) 1f else 0f, pressProgressAnimationSpec) }
            val scale = if (pressed) pressedScale else initialScale
            launch { scaleXAnimation.animateTo(scale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(scale, scaleYAnimationSpec) }
        }
    }

    suspend fun snapToValue(value: Float) {
        valueAnimation.snapTo(value.coerceIn(valueRange))
    }

    suspend fun settleToValue(value: Float) {
        valueAnimation.animateTo(value.coerceIn(valueRange), valueAnimationSpec)
    }
}
