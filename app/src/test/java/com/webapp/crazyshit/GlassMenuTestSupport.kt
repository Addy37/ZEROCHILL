package com.webapp.crazyshit

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import org.junit.Assert.*
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** Inspect and activate real composed menu semantics without adding a UI-test dependency. */
object GlassMenuTestSupport {
    @JvmStatic fun assertGlass(popup: PopupWindow) {
        settle()
        assertTrue("Popup must stay open after composition attaches", popup.isShowing)
        assertEquals("preference_glass_panel", popup.contentView.tag)
        val compose = views(popup.contentView).filterIsInstance<ComposeView>().single()
        assertTrue("The liquid glass popup must have a live composition", compose.hasComposition)
        val root = popup.contentView.rootView
        assertNotNull(root.findViewTreeLifecycleOwner())
        assertSame(root.findViewTreeLifecycleOwner(), root.findViewTreeSavedStateRegistryOwner())
    }

    @JvmStatic fun description(root: View, label: String): View? =
        views(root).firstOrNull { it.contentDescription?.toString() == label }

    @JvmStatic fun owner(popup: PopupWindow): LifecycleOwner =
        requireNotNull(popup.contentView.rootView.findViewTreeLifecycleOwner())

    @JvmStatic fun hasAction(popup: PopupWindow, label: String): Boolean {
        settle()
        return actionNode(popup, label) != null
    }

    @JvmStatic fun click(popup: PopupWindow, label: String): Boolean {
        settle()
        val node = actionNode(popup, label) ?: return false
        return node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
    }

    private fun actionNode(popup: PopupWindow, label: String): SemanticsNode? {
        val owner = views(popup.contentView).firstNotNullOfOrNull { view ->
            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                ?.invoke(view) as? SemanticsOwner
        } ?: error("Missing attached Compose semantics owner")
        fun find(node: SemanticsNode): SemanticsNode? {
            val labels = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            if (label in labels && node.config.getOrNull(SemanticsActions.OnClick)?.action != null) return node
            return node.children.firstNotNullOfOrNull { find(it) }
        }
        return find(owner.rootSemanticsNode)
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }

    private fun settle() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400)) }
}
