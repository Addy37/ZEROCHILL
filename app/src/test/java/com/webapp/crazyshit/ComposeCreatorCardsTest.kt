package com.webapp.crazyshit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeCreatorCardsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        listOf("creator_favorites", CreatorCatalog.PREFS, "manual_creator_merges_v1",
            "creator_avatar_overrides_v1", "zerochill_update_inbox_v1").forEach {
            context.getSharedPreferences(it, 0).edit().clear().commit()
        }
        ManualCreatorMergeStore.clearCacheForTest()
        CreatorAvatarOverrideStore.clearCacheForTest()
    }

    private fun seed(names: List<String>) {
        context.getSharedPreferences("creator_favorites", 0).edit()
            .putStringSet("creators", names.map { it.lowercase() }.toSet()).commit()
        CreatorCatalog.remember(context, names.map { NativeContentItem(NativeContentItem.KIND_CREATOR,
            it, "https://fapello.com/${it.lowercase()}/", "", "", "", "", "", it) })
    }

    private fun options(name: String) {
        compose.onNodeWithText(name).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
    }

    @Test fun anchoredOptionsRemoveOnlyChosenCreatorAndExposeMergeTargets() {
        seed(listOf("Alpha", "Beta"))
        Robolectric.buildActivity(CreatorsActivity::class.java).setup().use { host ->
            compose.onNodeWithText("Alpha").assertIsDisplayed()
            compose.onNodeWithText("⋮").assertDoesNotExist()
            options("Alpha")
            compose.onNodeWithText("Merge with…").performClick()
            compose.onAllNodesWithText("Beta").assertCountEquals(2)
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("Change avatar").assertIsDisplayed()
            compose.onNodeWithText("Remove from favorites").performClick()
            compose.onNodeWithText("Alpha").assertDoesNotExist()
            compose.onNodeWithText("Beta").assertIsDisplayed()
            assertEquals(1, CreatorCatalog.favoriteGroups(host.get()).size)
        }
    }

    @Test fun mergedCreatorsAndCustomAvatarActionsStayAvailable() {
        seed(listOf("Alpha", "Beta"))
        assertTrue(ManualCreatorMergeStore.merge(context, setOf("alpha"), setOf("beta"), "alpha", "alpha"))
        assertTrue(CreatorAvatarOverrideStore.save(context, setOf("alpha", "beta"),
            "https://fixture.invalid/avatar.jpg", "https://fixture.invalid/", .25f, .75f, 2f))
        Robolectric.buildActivity(CreatorsActivity::class.java).setup().use { host ->
            compose.onNodeWithText("2 profiles").assertIsDisplayed()
            options("Alpha")
            compose.onNodeWithText("Merged creators").assertExists()
            compose.onNodeWithText("Change primary").assertExists()
            compose.onNodeWithText("Use default avatar").performClick()
            assertFalse(CreatorAvatarOverrideStore.has(host.get(), setOf("alpha", "beta")))
            options("Alpha")
            compose.onNodeWithText("Unmerge").performClick()
            compose.onNodeWithText("Alpha").assertIsDisplayed()
            compose.onNodeWithText("Beta").assertIsDisplayed()
            assertEquals(2, CreatorCatalog.favoriteGroups(host.get()).size)
        }
    }

    @Test fun unreadCountsRenderWithoutDominatingTheAvatar() {
        seed(listOf("Alpha"))
        val entry = UpdateInboxStore.Entry().apply {
            id = "fixture-unread"; fingerprint = "fixture-unread"
            category = UpdateInboxStore.CATEGORY_ONLYFAP; creatorName = "Alpha"; count = 7
        }
        context.getSharedPreferences("zerochill_update_inbox_v1", 0).edit()
            .putString("entries", org.json.JSONArray().put(entry.encode()).toString()).commit()
        Robolectric.buildActivity(CreatorsActivity::class.java).setup().use {
            compose.onNodeWithContentDescription("7 new items from Alpha", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("7", useUnmergedTree = true).assertExists()
        }
    }

    @Test fun scrollRestoresAfterRotationWithoutWarmingGalleries() {
        seed((0 until 40).map { "Creator%03d".format(it) })
        Robolectric.buildActivity(CreatorsActivity::class.java).setup().use { host ->
            compose.waitForIdle()
            var grid = ReflectionHelpers.getField<RecyclerView>(host.get(), "recycler")
            grid.scrollToPosition(20)
            compose.waitForIdle()
            val before = (grid.layoutManager as GridLayoutManager).findFirstVisibleItemPosition()
            assertTrue(before > 0)
            host.recreate()
            compose.waitForIdle()
            grid = ReflectionHelpers.getField(host.get(), "recycler")
            assertEquals(before, (grid.layoutManager as GridLayoutManager).findFirstVisibleItemPosition())
        }
    }

    @Test fun longNamesAtDoubleFontHaveCompactBrandedAvatarsAndReadableLabels() {
        RuntimeEnvironment.setFontScale(2f)
        try {
            val name = "Alexandria Very Long Creator Name"
            seed(listOf(name))
            Robolectric.buildActivity(CreatorsActivity::class.java).setup().use { host ->
                compose.onNodeWithText(name).assertIsDisplayed()
                val grid = ReflectionHelpers.getField<RecyclerView>(host.get(), "recycler")
                assertEquals(1, (grid.layoutManager as GridLayoutManager).spanCount)
                val holder = grid.findViewHolderForAdapterPosition(0)!!
                val avatar = ReflectionHelpers.getField<CreatorAvatarImageView>(holder, "avatar")
                assertEquals(BrowseUi.dp(host.get(), 56), avatar.width)
                assertNotNull(avatar.drawable)
                val bounds = compose.onNodeWithText(name).fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.height > 50f)
                assertTrue(bounds.right <= 360f)
                render("compose-creators-double-font", host.get().window.decorView)
            }
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    private fun render(name: String, view: View) {
        compose.waitForIdle()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val dir = File("build/reports/visual-tests").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
