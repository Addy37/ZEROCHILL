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
class ComposeSearchTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun recentSearchRowsRunRemoveAndClearThroughCompose() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            var repeated = ""
            var removed = ""
            var cleared = false
            val recent = RecentSearchesCompose(host.get(), { repeated = it }, { removed = it }, { cleared = true })
            try {
                recent.show(listOf("Alpha", "Beta"))
                host.get().setContentView(recent.view)
                compose.onNodeWithContentDescription("Search again for Alpha").performClick()
                assertEquals("Alpha", repeated)
                compose.onNodeWithContentDescription("Remove recent search Beta").performClick()
                assertEquals("Beta", removed)
                compose.onNodeWithContentDescription("Clear all recent searches").performClick()
                assertTrue(cleared)
            } finally { recent.close() }
        }
    }

    @Test fun lazySearchKeepsImagesAndPublishesProgressiveSnapshots() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            var opened = ""
            val adapter = OnlyFapCreatorSearchAdapter { opened = it.url }
            val results = SearchResultsCompose(host.get())
            try {
                results.setAdapter(adapter)
                host.get().setContentView(results.view)
                adapter.replace(listOf(creator("First")))
                compose.waitForIdle()
                assertEquals(1, results.getItemCount())
                compose.onNodeWithContentDescription("First, OnlyFap creator").assertExists()
                val first = images(results.view).single()
                adapter.replace(listOf(creator("First"), creator("Second")))
                compose.waitForIdle()
                saveRender("compose-search-progressive", results.view)
                assertEquals(2, results.getItemCount())
                assertSame(first, images(results.view).first())
                assertFalse(adapter.composeFirstPresentation(adapter.composeKey(0)))
                compose.onNodeWithContentDescription("Second, OnlyFap creator").performClick()
                assertEquals("https://fapello.com/second/", opened)
                adapter.replace(emptyList())
                compose.waitForIdle()
                compose.onNodeWithContentDescription("First, OnlyFap creator").assertDoesNotExist()
            } finally { results.close() }
        }
    }

    @Test fun searchScrollSnapshotRestoresWithoutARecyclerView() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            val results = SearchResultsCompose(host.get())
            val adapter = OnlyFapCreatorSearchAdapter { }
            try {
                results.setAdapter(adapter)
                adapter.replace((0..30).map { creator("Creator$it") })
                host.get().setContentView(results.view)
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(15)
                compose.waitForIdle()
                val saved = results.saveScroll() as Bundle
                assertTrue(saved.getInt("compose_index") > 0)
                results.scrollToPosition(0)
                compose.waitForIdle()
                results.restoreScroll(saved)
                compose.waitForIdle()
                assertEquals(saved.getInt("compose_index"), (results.saveScroll() as Bundle).getInt("compose_index"))
            } finally { results.close() }
        }
    }

    @Test fun mixedProviderInsertAndMetadataUpdateUseCurrentSnapshots() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            val adapter = GlobalSearchAdapter { }
            val results = SearchResultsCompose(host.get())
            try {
                results.setAdapter(adapter)
                host.get().setContentView(results.view)
                fun video(title: String, url: String) = NativeContentItem(NativeContentItem.KIND_MEDIA, title, url, "", "", "", "")
                adapter.replace(listOf(GlobalSearchAdapter.Entry.section("Videos"),
                    GlobalSearchAdapter.Entry.item(video("Retained", "https://fixture.invalid/1"), 0),
                    GlobalSearchAdapter.Entry.section("Your Library"),
                    GlobalSearchAdapter.Entry.item(video("Old title", "https://fixture.invalid/2"), 1)))
                compose.waitForIdle()
                val first = images(results.view).first()
                adapter.replace(listOf(GlobalSearchAdapter.Entry.section("Videos"),
                    GlobalSearchAdapter.Entry.item(video("Retained", "https://fixture.invalid/1"), 0),
                    GlobalSearchAdapter.Entry.item(video("New provider", "https://fixture.invalid/3"), 0),
                    GlobalSearchAdapter.Entry.section("Your Library"),
                    GlobalSearchAdapter.Entry.item(video("Updated title", "https://fixture.invalid/2"), 1)))
                compose.onNodeWithText("Updated title").assertExists()
                compose.onNodeWithText("New provider").assertExists()
                compose.onNodeWithText("Old title").assertDoesNotExist()
                assertSame(first, images(results.view).first())
            } finally { results.close(); adapter.close() }
        }
    }

    @Test fun composeCreatorFavoriteAndLongPressRetainExistingStore() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            val creator = creator("Alpha")
            host.get().getSharedPreferences("creator_favorites", 0).edit().clear().commit()
            val results = SearchResultsCompose(host.get())
            val adapter = OnlyFapCreatorSearchAdapter { }
            try {
                results.setAdapter(adapter)
                adapter.replace(listOf(creator))
                host.get().setContentView(results.view)
                compose.onNodeWithContentDescription("Favorite Alpha").performClick()
                assertTrue(CreatorFavoriteStore.contains(host.get(), creator))
                compose.onNodeWithContentDescription("Unfavorite Alpha").assertExists()
                compose.onNodeWithContentDescription("Alpha, OnlyFap creator").performTouchInput { longClick() }
                assertFalse(CreatorFavoriteStore.contains(host.get(), creator))
            } finally { results.close() }
        }
    }

    @Test fun sameCreatorInANewQueryClaimsANewEntrance() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { host ->
            val results = SearchResultsCompose(host.get())
            val adapter = OnlyFapCreatorSearchAdapter { }
            try {
                results.setAdapter(adapter)
                host.get().setContentView(results.view)
                adapter.replace(listOf(creator("Alpha")))
                compose.waitForIdle()
                assertFalse(adapter.composeFirstPresentation(adapter.composeKey(0)))
                adapter.resetEntrance()
                adapter.replace(emptyList())
                adapter.replace(listOf(creator("Alpha")))
                compose.waitForIdle()
                assertFalse(adapter.composeFirstPresentation(adapter.composeKey(0)))
                compose.onNodeWithContentDescription("Alpha, OnlyFap creator").assertExists()
            } finally { results.close() }
        }
    }

    private fun images(view: View): List<android.widget.ImageView> = when (view) {
        is android.widget.ImageView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { images(view.getChildAt(it)) }
        else -> emptyList()
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
