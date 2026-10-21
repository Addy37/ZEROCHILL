package com.webapp.crazyshit

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeLibraryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun watchLaterQueryRotationAndSelectionKeepExistingResultContract() {
        val intent = Intent().putExtra(FavoritesActivity.EXTRA_START_TAB, FavoritesActivity.START_WATCH_LATER)
        Robolectric.buildActivity(FavoritesActivity::class.java, intent).setup().use { host ->
            FavoriteStore.clear(host.get())
            FavoriteStore.add(host.get(), "Alpha video", "https://fixture.invalid/alpha")
            FavoriteStore.add(host.get(), "Beta video", "https://fixture.invalid/beta")
            host.pause().resume()
            saveRender("compose-library-watch-later", host.get().window.decorView)
            compose.onNodeWithText("Search Watch Later").performTextInput("Alpha")
            compose.onNodeWithText("1 of 2 videos · Search results").assertExists()
            host.recreate()
            compose.onNodeWithText("Alpha").assertExists()
            compose.waitForIdle()
            val card = find(host.get().window.decorView, "Watch Later Alpha video")
            assertNotNull(card)
            card!!.performClick()
            assertEquals("https://fixture.invalid/alpha", shadowOf(host.get()).resultIntent
                .getStringExtra(FavoritesActivity.EXTRA_SELECTED_URL))
            assertEquals(FavoritesActivity.START_WATCH_LATER, shadowOf(host.get()).resultIntent
                .getIntExtra(FavoritesActivity.EXTRA_SELECTED_TAB, -1))
            assertEquals(2, FavoriteStore.load(host.get()).size)
        }
    }

    @Test fun standaloneLibraryRoutesHistoryWithoutChangingCollections() {
        Robolectric.buildActivity(LibraryHubActivity::class.java).setup().use { host ->
            compose.onNodeWithText("History").performScrollTo().performClick()
            val next = shadowOf(host.get()).nextStartedActivity
            assertEquals(FavoritesActivity::class.java.name, next.component!!.className)
            assertEquals(FavoritesActivity.START_HISTORY, next.getIntExtra(FavoritesActivity.EXTRA_START_TAB, -1))
        }
    }

    @Test fun libraryHomeMountsHeaderAndInitialLoadingInsideComposeScroll() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            val home = LibraryHubView(host.get(), null)
            try {
                host.get().setContentView(home)
                compose.waitForIdle()
                assertNotNull(find(home, "Loading your Library"))
                assertNotNull(find(home, "Settings"))
                compose.onNode(hasScrollAction()).assertExists()
            } finally { home.close() }
        }
    }

    @Test fun libraryHomeRestoresScrollFromItsViewState() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            fun home(): LibraryHubView = LibraryHubView(host.get(), null).also { home ->
                val content = org.robolectric.util.ReflectionHelpers.getField<android.widget.LinearLayout>(home, "content")
                content.addView(android.view.View(host.get()), android.widget.LinearLayout.LayoutParams(1,
                    (2000 * host.get().resources.displayMetrics.density).toInt()))
            }
            val first = home()
            val second = home()
            try {
                host.get().setContentView(first)
                compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
                compose.waitForIdle()
                val saved = org.robolectric.util.ReflectionHelpers.callInstanceMethod<android.os.Parcelable>(first, "onSaveInstanceState")
                val position = (saved as Bundle).getInt("scroll")
                assertTrue(position > 0)
                org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(second, "onRestoreInstanceState",
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.os.Parcelable::class.java, saved))
                host.get().setContentView(second)
                compose.waitForIdle()
                val restored = org.robolectric.util.ReflectionHelpers.callInstanceMethod<Bundle>(second, "onSaveInstanceState")
                assertEquals(position, restored.getInt("scroll"))
            } finally { first.close(); second.close() }
        }
    }

    @Test fun libraryHeaderSpacerScrollsWithContentAndKeepsItsRange() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            val frame = android.widget.FrameLayout(host.get())
            val content = android.widget.LinearLayout(host.get())
            content.addView(View(host.get()), android.widget.LinearLayout.LayoutParams(1, 3000))
            val presentation = LibraryHomeCompose(host.get(), frame, content)
            try {
                presentation.headerInsetPx = 80
                frame.addView(presentation.view, android.widget.FrameLayout.LayoutParams(-1, -1))
                host.get().setContentView(frame)
                compose.waitForIdle()
                assertEquals(3080 - presentation.view.height, presentation.maximumScroll())
                fun contentTop(): Int {
                    val bounds = android.graphics.Rect(0, 0, content.width, content.height)
                    presentation.view.offsetDescendantRectToMyCoords(content, bounds)
                    return bounds.top
                }
                assertEquals(80, contentTop())
                compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
                compose.waitForIdle()
                assertTrue(presentation.scrollPosition() > 80)
                assertEquals(80 - presentation.scrollPosition(), contentTop())
            } finally { presentation.close() }
        }
    }

    private fun saveRender(name: String, view: View) {
        compose.waitForIdle()
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val directory = java.io.File("build/reports/visual-tests").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(directory, "$name.png")).use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun creator(name: String) = NativeContentItem(NativeContentItem.KIND_CREATOR, name,
        "https://fapello.com/${name.lowercase()}/", "", "", "", "", "Fapello", name)

    private fun find(root: View, description: String): View? {
        if (root.contentDescription?.toString() == description) return root
        if (root is ViewGroup) for (index in 0 until root.childCount) {
            find(root.getChildAt(index), description)?.let { return it }
        }
        return null
    }
}
