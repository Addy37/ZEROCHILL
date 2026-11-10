package com.webapp.crazyshit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupWindow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.function.Consumer
import kotlin.math.min

/**
 * Presentation-only Compose menu on top of the existing anchored PopupWindow contract.
 * Native menu items still own visibility, enabled state, IDs and action callbacks.
 * PreferenceGlassPanel supplies the real recorded-page lens on supported devices.
 */
internal object ZeroChillComposeMenu {
    private val TextInk = Color(0xFFF5F7FA)
    private val TextMuted = Color(0xFF929DAC)
    private val DividerInk = Color(0xFF36404B)

    /** Makes menu filtering and order independently testable without a rendering host. */
    @JvmStatic
    fun visibleItems(menu: Menu): List<MenuItem> =
        (0 until menu.size()).map { menu.getItem(it) }.filter { it.isVisible }

    @JvmStatic
    @JvmOverloads
    fun show(context: Context, anchor: View, menu: Menu, onSelected: Consumer<MenuItem>, onDismiss: Runnable? = null, above: Boolean = false): Boolean {
        val activity = activityFor(context) ?: return false
        if (!anchor.isAttachedToWindow) return false
        val items = visibleItems(menu)
        if (items.isEmpty()) return false

        // A PopupWindow lives in its own window. Give this ComposeView a dedicated
        // local owner rather than relying on the Activity's window hierarchy.
        val owner = NavOwner(activity)
        val width = min(dp(context, 270), context.resources.displayMetrics.widthPixels - dp(context, 32))
        val maxHeight = AnchoredMenuPopup.maxHeight(anchor,
            min(dp(context, 420), context.resources.displayMetrics.heightPixels - dp(context, 96)), above)
        lateinit var popup: PopupWindow
        val content = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ComposeMenu(
                    items = items,
                    accent = Color(AppearanceTheme.accent(activity)),
                    menuTint = AppearanceTheme.menuTint(activity),
                    maxHeightDp = maxHeight / resources.displayMetrics.density,
                    colorSplitting = AppearanceTheme.glassColorSplitting(activity),
                    edgeStrength = PreferenceGlassPanel.edgeStrength(activity),
                    onChoose = { item ->
                        popup.dismiss()
                        onSelected.accept(item)
                    },
                )
            }
        }

        val glass = PreferenceGlassPanel(activity, content).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            // WindowRecomposer looks up the popup decor root, not just ComposeView.
            // This parent attaches before its Compose child, so install the window
            // owners here before that child attempts to create its composition.
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.rootView.setViewTreeLifecycleOwner(owner)
                    view.rootView.setViewTreeSavedStateRegistryOwner(owner)
                }
                override fun onViewDetachedFromWindow(view: View) = Unit
            })
        }
        popup = PopupWindow(glass, width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            isOutsideTouchable = true
            isClippingEnabled = true
            elevation = dp(context, 13).toFloat()
            if (ZeroChillMotion.animationsEnabled(context)) {
                animationStyle = R.style.Animation_ZeroChill_Menu
            }
        }
        val attachment = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { popup.dismiss() }
        }
        anchor.addOnAttachStateChangeListener(attachment)
        popup.setOnDismissListener {
            anchor.removeOnAttachStateChangeListener(attachment)
            owner.close()
            onDismiss?.run()
        }

        return try {
            AnchoredMenuPopup.show(popup, anchor, above)
            true
        } catch (e: android.view.WindowManager.BadTokenException) {
            anchor.removeOnAttachStateChangeListener(attachment)
            owner.close()
            false
        }
    }

    private fun activityFor(context: Context): Activity? {
        var current: Context? = context
        while (current != null) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    @Composable
    private fun ComposeMenu(
        items: List<MenuItem>,
        accent: Color,
        menuTint: Float,
        maxHeightDp: Float,
        colorSplitting: Boolean,
        edgeStrength: Float,
        onChoose: (MenuItem) -> Unit,
    ) {
        val rim: Brush = if (colorSplitting && android.os.Build.VERSION.SDK_INT >= 33) {
            Brush.linearGradient(
                listOf(accent.copy(alpha = (.9f * edgeStrength).coerceAtMost(1f)), Color(0xFF9B7BFF).copy(alpha = (.7f * edgeStrength).coerceAtMost(1f)),
                    Color(0xFF66DBE5).copy(alpha = (.7f * edgeStrength).coerceAtMost(1f)), accent.copy(alpha = (.9f * edgeStrength).coerceAtMost(1f))),
            )
        } else {
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = (.4f * edgeStrength).coerceAtMost(1f)), accent.copy(alpha = (.42f * edgeStrength).coerceAtMost(1f)),
                    Color.White.copy(alpha = (.16f * edgeStrength).coerceAtMost(1f))),
            )
        }
        val shape = remember { RoundedCornerShape(22.dp) }
        val surfaceOpacity = (.68f + menuTint * .16f).coerceIn(0f, 1f)

        MaterialTheme(colorScheme = darkColorScheme(primary = accent)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeightDp.dp)
                    .background(Color(0xFF0C121B).copy(alpha = surfaceOpacity), shape)
                    .border(BorderStroke(1.dp, rim), shape)
                    .verticalScroll(rememberScrollState())
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEachIndexed { index, item ->
                    if (index != 0) HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = .6.dp,
                        color = DividerInk.copy(alpha = .7f),
                    )
                    val enabled = item.isEnabled
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = enabled) { onChoose(item) }
                            .semantics { contentDescription = item.title?.toString().orEmpty() }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = item.title?.toString().orEmpty(),
                            color = if (enabled) TextInk else TextMuted,
                            fontSize = 14.sp,
                            fontWeight = if (item.isChecked) FontWeight.SemiBold else FontWeight.Medium,
                            lineHeight = 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.isCheckable && item.isChecked) {
                            Text("✓", color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
