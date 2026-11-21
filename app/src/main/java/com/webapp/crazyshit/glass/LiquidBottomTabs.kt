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
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.webapp.crazyshit.glass.backdrop.BackdropEffectScope
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.webapp.crazyshit.glass.backdrop.Backdrop
import com.webapp.crazyshit.glass.backdrop.backdrops.layerBackdrop
import com.webapp.crazyshit.glass.backdrop.backdrops.rememberLayerBackdrop
import com.webapp.crazyshit.glass.backdrop.drawBackdrop
import com.webapp.crazyshit.glass.backdrop.effects.blur
import com.webapp.crazyshit.glass.backdrop.effects.lens
import com.webapp.crazyshit.glass.backdrop.effects.vibrancy
import com.webapp.crazyshit.glass.backdrop.highlight.Highlight
import com.webapp.crazyshit.glass.backdrop.shadow.InnerShadow
import com.webapp.crazyshit.glass.backdrop.shadow.Shadow
import com.webapp.crazyshit.glass.shapes.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

// The tinted copy is a drawing source, not another shared-transition endpoint.
internal val LocalLiquidBottomTabsReplica = staticCompositionLocalOf { false }
// The geometric shell is a sibling, captured before the selector is drawn.
internal val LocalLiquidBottomTabsSurfaceBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    // LOCAL CHANGE: upstream picks these from the system light/dark setting and
    // fills the capsule at 40% opacity, which made this bar read as a solid
    // white slab next to the other glass on screen. They are parameters now, and
    // this app passes a much lighter film so the bar is the same material as
    // everything else. Upstream values kept as the defaults.
    accentColor: Color = Color(0xFF0091FF),
    containerColor: Color = Color(0xFF121212).copy(0.4f),
    // LOCAL CHANGE: upstream pins the bar at 64dp. The player wants a slimmer
    // version of the same control, and copying the file to change one number
    // would leave two of them to keep in step.
    height: Dp = 64f.dp,
    // LOCAL CHANGE: how deep the capsule refracts what is behind it. Upstream's
    // 24dp is tuned for the 64dp bar; on a shorter one the bend coming down from
    // the top edge and the bend coming up from the bottom meet in the middle and
    // leave a hard horizontal seam across the glass. Keep it below half the
    // height.
    lensDepth: Dp = 24f.dp,
    // LOCAL CHANGE: hides the moving capsule without moving it.
    //
    // The bar always has a selected index — it is what the indicator is drawn
    // at — but the app has screens that are none of the tabs, and on those the
    // indicator claimed the app was somewhere it was not. Faded rather than
    // removed, so it comes back where it left and the drag target stays alive.
    indicatorVisible: Boolean = true,
    /**
     * LOCAL CHANGE: what the moving indicator is filled with.
     *
     * Upstream lightens the slot it marks — white at a tenth on a dark bar. The
     * reference this app is matched against does the opposite: it sinks the lit
     * slot into the bar, so the mark is darker than the glass around it. Null
     * keeps upstream's.
     */
    indicatorColor: Color? = null,
    drawContainer: Boolean = true,
    // ZEROCHILL adaptation: retained ViewPager synchronization and live fake-drag.
    pagerPosition: (() -> Float)? = null,
    isPagerMoving: () -> Boolean = { false },
    animationsEnabled: Boolean = true,
    onNavigationDragStart: () -> Boolean = { false },
    onNavigationDragBy: (Float) -> Unit = {},
    onNavigationDragEnd: (Boolean) -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()

    val tabsBackdrop = rememberLayerBackdrop()
    val surfaceBackdrop = LocalLiquidBottomTabsSurfaceBackdrop.current

    // LOCAL CHANGE: the width is read where it is used, not in composition.
    //
    // Upstream measures itself with BoxWithConstraints, which composes its
    // content again for every width it is given. That is free for a bar that
    // stands still and ruinous for one that changes shape: as the bottom bar
    // folds, this group loses a little width on every frame, and every frame
    // recomposed all five tabs twice over (the row and its tinted copy), with
    // the page scrolling underneath. Nothing here needs the width before
    // layout: the drag, the lit slot and the highlight all ask for it when
    // they run.
    val widthPx = remember { mutableFloatStateOf(0f) }

    Box(
        modifier.onSizeChanged { widthPx.floatValue = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth: () -> Float = remember(density, tabsCount) {
            { with(density) { (widthPx.floatValue - 8f.dp.toPx()) / tabsCount } }
        }


        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var navigationDragStarted by remember { mutableStateOf(false) }
        var accumulatedDrag by remember { mutableFloatStateOf(0f) }
        val touchSlop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
        val currentMotion by androidx.compose.runtime.rememberUpdatedState(animationsEnabled)
        val dragOffset = remember { mutableFloatStateOf(0f) }
        var offsetSettle by remember { mutableStateOf<Job?>(null) }
        val settlePanel = {
            if (currentMotion && dragOffset.floatValue != 0f) {
                offsetSettle = animationScope.launch {
                    Animatable(dragOffset.floatValue).animateTo(0f, spring(1f, 300f, .5f)) {
                        dragOffset.floatValue = value
                    }
                    dragOffset.floatValue = 0f
                }
            } else dragOffset.floatValue = 0f
        }
        val panelOffset = {
            val width = widthPx.floatValue
            val fraction = if (currentMotion && width > 0f)
                (dragOffset.floatValue / width).coerceIn(-1f, 1f) else 0f
            with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                motionEnabled = { currentMotion },
                onDragStarted = {
                    offsetSettle?.cancel()
                    accumulatedDrag = 0f
                    navigationDragStarted = false
                },
                onDragCanceled = {
                    if (navigationDragStarted) onNavigationDragEnd(true)
                    navigationDragStarted = false
                    settlePanel()
                },
                onDragStopped = {
                    if (navigationDragStarted) onNavigationDragEnd(false)
                    else onTabSelected(selectedTabIndex())
                    navigationDragStarted = false
                    settlePanel()
                },
                onDrag = { _, dragAmount ->
                    val width = tabWidth()
                    if (width > 0f) {
                        val delta = dragAmount.x * if (isLtr) 1f else -1f
                        accumulatedDrag += delta
                        if (!navigationDragStarted && abs(accumulatedDrag) > touchSlop) {
                            navigationDragStarted = onNavigationDragStart()
                            if (navigationDragStarted) onNavigationDragBy(accumulatedDrag / width)
                        } else if (navigationDragStarted) {
                            onNavigationDragBy(delta / width)
                        }
                        if (currentMotion && navigationDragStarted) {
                            dragOffset.floatValue = accumulatedDrag * if (isLtr) 1f else -1f
                        }
                    }
                }
            )
        }
        val pressProgress = { if (currentMotion) dampedDragAnimation.pressProgress else 0f }
        val selectionFlash = remember { Animatable(0f) }
        LaunchedEffect(pagerPosition, isPagerMoving, animationsEnabled) {
            var wasMoving = false
            snapshotFlow { selectedTabIndex() to isPagerMoving() }.collectLatest { (index, moving) ->
                val followPager = wasMoving || moving
                wasMoving = moving
                val value = pagerPosition?.invoke() ?: index.toFloat()
                if (followPager || !currentMotion) dampedDragAnimation.snapToValue(value)
                else dampedDragAnimation.settleToValue(index.toFloat())
            }
        }
        LaunchedEffect(selectedTabIndex, animationsEnabled) {
            var previous = selectedTabIndex()
            selectionFlash.snapTo(0f)
            snapshotFlow { selectedTabIndex() }.collectLatest { index ->
                if (animationsEnabled && index != previous) {
                    previous = index
                    selectionFlash.snapTo(1f)
                    selectionFlash.animateTo(0f, tween(240))
                }
            }
        }
        val slotPosition = {
            if (isPagerMoving()) pagerPosition?.invoke() ?: dampedDragAnimation.value
            else dampedDragAnimation.value
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    val center = (slotPosition() + .5f) * tabWidth()
                    Offset((if (isLtr) center else size.width - center) + panelOffset(),
                        size.height / 2f)
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset() }
                // LOCAL CHANGE: the shell is made of the app's glass, like every
                // other pane. The puck below keeps its own recipe on purpose —
                // what it does is press-driven, a bend and a rim that only exist
                // while a finger is on it, which is not a material.
                .then(if (drawContainer) Modifier.liquidGlass(
                    config = LocalGlassEffectConfig.current,
                    shape = ContinuousCapsule(),
                    // Follows the setting like everything else; the shell of a
                    // panel switcher is not a different material from the bar.
                    blurRadiusDp = LocalGlassEffectConfig.current.blurRadius,
                    ownBackdrop = backdrop,
                    layerBlock = {
                        val progress = pressProgress()
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                ) else Modifier)
                // Clip the row's rectangular touch glow, not its selector siblings.
                .clip(ContinuousCapsule())
                .then(if (animationsEnabled) interactiveHighlight.modifier else Modifier)
                .height(height)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabsReplica provides true,
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, pressProgress())
            }
        ) {
            // LOCAL CHANGE: only the tabs, tinted, on nothing.
            //
            // This copy used to carry the page and the bar's film under the
            // tabs, and the lit slot drew all of it through itself and then laid
            // its own wash on top. The wash is what makes the slot, and on top
            // it darkened the one tab that has to stand out: at the dark side's
            // strength a white tab came out a flat mid grey. The page now comes
            // through the slot on its own, and this is drawn over the wash; see
            // the two layers below.
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset() }
                    .height(height - 8f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        val indicatorAlpha by animateFloatAsState(
            targetValue = if (indicatorVisible) 1f else 0f,
            animationSpec = tween(220),
            label = "indicatorAlpha",
        )

        // Where the slot is and how it is bent, shared by its two layers so
        // they move and stretch as one.
        val slotPlacement = Modifier
            .padding(horizontal = 4f.dp)
            .graphicsLayer {
                alpha = indicatorAlpha
                val center = slotPosition() * tabWidth()
                translationX = (if (isLtr) center else -center) + panelOffset()
            }
        // Stretch the two layers together at draw time without remeasuring the dock.
        val slotGrowth = Modifier.graphicsLayer {
            if (animationsEnabled) {
                val selection = selectionFlash.value
                scaleX = maxOf(dampedDragAnimation.scaleX, 1f + .24f * selection)
                scaleY = maxOf(dampedDragAnimation.scaleY, 1f + .13f * selection)
            } else {
                scaleX = 1f
                scaleY = 1f
            }
        }
        val lensConfig = LocalGlassEffectConfig.current
        val lensEnabled = lensConfig.lensHeight > 0f && lensConfig.lensAmount > 0f
        // Keep the original selected-ink optics and all placement/drag springs.
        val slotLens: BackdropEffectScope.() -> Unit = {
            if (lensEnabled) {
                val progress = pressProgress()
                lens(
                    10f.dp.toPx() * progress,
                    14f.dp.toPx() * progress,
                    chromaticAberration = true
                )
            }
        }
        // The live page stays a thicker lens even after release. Magnification
        // affects only that input, so ZEROCHILL's glyph sizes remain unchanged.
        val dropletLens: BackdropEffectScope.() -> Unit = {
            if (lensEnabled) {
                val progress = pressProgress().coerceIn(0f, 1f)
                lens(
                    (8f + 3f * progress).dp.toPx(),
                    (12f + 4f * progress).dp.toPx(),
                    magnification = 1.08f + .04f * progress,
                )
            }
        }

        val pressedBackdrop = remember(backdrop, surfaceBackdrop, dampedDragAnimation) {
            object : Backdrop {
                private val surfacePaint = androidx.compose.ui.graphics.Paint()
                override val isCoordinatesDependent = true
                override fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackdrop(
                    density: androidx.compose.ui.unit.Density,
                    coordinates: androidx.compose.ui.layout.LayoutCoordinates?,
                    layerBlock: (GraphicsLayerScope.() -> Unit)?,
                ) {
                    // Fade the actual shell in and out continuously. Selecting
                    // it until progress reaches exactly zero stacked two films
                    // on release, then removed one abruptly: a white flash.
                    with(backdrop) { drawBackdrop(density, coordinates, layerBlock) }
                    val progress = pressProgress().coerceIn(0f, 1f)
                    if (surfaceBackdrop != null && progress > 0f) {
                        surfacePaint.alpha = progress
                        val canvas = drawContext.canvas
                        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), surfacePaint)
                        try {
                            with(surfaceBackdrop) { drawBackdrop(density, coordinates, layerBlock) }
                        } finally {
                            canvas.restore()
                        }
                    }
                }
            }
        }

        // Clear live page, restrained wash, then the original rim and shadows.
        Box(
            slotPlacement
                .then(if (animationsEnabled) interactiveHighlight.gestureModifier else Modifier)
                .then(dampedDragAnimation.modifier)
                .height(height - 8f.dp)
                .fillMaxWidth(1f / tabsCount)
                .then(slotGrowth)
                .drawBackdrop(
                    backdrop = pressedBackdrop,
                    shape = { ContinuousCapsule() },
                    effects = {
                        vibrancy()
                        val progress = pressProgress().coerceIn(0f, 1f)
                        blur((1f - .75f * progress).dp.toPx())
                        dropletLens()
                    },
                    highlight = {
                        val progress = pressProgress()
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = pressProgress()
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = pressProgress()
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    onDrawSurface = {
                        val progress = pressProgress()
                        drawRect(containerColor, alpha = 1f - progress)
                        drawRect(
                            indicatorColor ?: if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        drawRect(accentColor, alpha = .25f * selectionFlash.value)
                    }
                )
        )

        // LOCAL CHANGE: the lit tab, over its slot rather than under the wash.
        //
        // With Square's original ink refraction and the same drag, so while a
        // finger is on the bar it still reads as one piece of glass; no rim or
        // shadow of its own, those belong to the slot. Nothing takes touches
        // here, so the slot underneath still gets them.
        Box(
            slotPlacement
                .height(height - 8f.dp)
                .fillMaxWidth(1f / tabsCount)
                .then(slotGrowth)
                .drawBackdrop(
                    backdrop = tabsBackdrop,
                    shape = { ContinuousCapsule() },
                    effects = slotLens,
                    highlight = null,
                    shadow = null,
                )
        )
    }
}
