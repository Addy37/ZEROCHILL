package com.webapp.crazyshit

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

/** Retains the native pane/footer/IME assertions while checking the actual Compose result. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeSearchPaneGeometryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun lastCreatorAndFooterStayReachableWithInternalBottomSpaceAndIme() {
        Robolectric.buildActivity(Activity::class.java).setup().use { host ->
            val owner = SearchGlassOwner(host.get())
            val panel = SearchPanel(host.get(), Intent().putExtra("scope", "bunkr"), true) { }
            try {
                panel.create(null)
                host.get().setContentView(panel.view)
                panel.field().setText("fixture")
                ReflectionHelpers.callInstanceMethod<Unit>(panel, "cancelSearchWork")
                val adapter = ReflectionHelpers.getField<OnlyFapCreatorSearchAdapter>(panel, "onlyFapAdapter")
                adapter.replace((0 until 20).map { CreatorCatalog.fromModel(
                    FapelloRepository.Model("Creator $it", "https://fapello.com/test-$it/", "")) })
                assertEquals(20, adapter.itemCount)
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(java.time.Duration.ofMillis(200))
                val footer = ReflectionHelpers.getField<TextView>(panel, "searchState")
                footer.text = "20 creators"
                footer.visibility = View.VISIBLE
                ReflectionHelpers.getField<View>(panel, "progress").visibility = View.GONE
                val viewport = ReflectionHelpers.getField<SearchResultsCompose>(panel, "results")
                val list = viewport.view
                val rows = ReflectionHelpers.getField<ViewGroup>(panel, "resultsControls")
                val pane = ReflectionHelpers.getField<View>(panel, "resultSurface")
                for (ime in listOf(0, 300)) {
                    val insets = WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 20))
                        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime)).build()
                    ViewCompat.dispatchApplyWindowInsets(panel.view, insets)
                    layout(panel.view)
                    viewport.scrollToPosition(19)
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                        .idleFor(java.time.Duration.ofMillis(100))
                    compose.waitForIdle()
                    layout(panel.view)
                    compose.waitForIdle()
                    val last = compose.onNodeWithContentDescription("Creator 19, OnlyFap creator")
                        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertTrue(last.bottom <= list.height)
                    assertTrue(last.top >= 0)
                    assertFalse(viewport.canScrollForward())
                    assertEquals(BrowseUi.dp(host.get(), 14), rows.paddingBottom)
                    val bounds = Rect(0, 0, footer.width, footer.height)
                    rows.offsetDescendantRectToMyCoords(footer, bounds)
                    assertTrue(bounds.bottom <= rows.height - rows.paddingBottom)
                    assertTrue(bounds.top >= 0)
                    assertTrue(footer.height > 0)
                    assertEquals(pane.left, rows.left)
                    assertEquals(pane.top, rows.top)
                    assertEquals(pane.right, rows.right)
                    assertEquals(pane.bottom, rows.bottom)
                    val slot = list.parent as FrameLayout
                    assertTrue(slot.clipChildren)
                    assertEquals(slot.height, list.bottom)
                }
            } finally { panel.close(); owner.close() }
        }
    }

    private fun layout(view: View) {
        repeat(2) {
            view.forceLayout()
            view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 400, 800)
        }
    }
}
