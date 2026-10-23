package com.webapp.crazyshit

import android.app.Activity
import android.app.Application
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HeaderGlassMaterialTest {
    @Test fun searchStatesShareClearNavbarOpticsWithRestrainedLocalFilm() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val nav = navigationGlassConfig(activity)
        val results = searchResultsGlassConfig(activity)
        val recent = recentSearchGlassConfig(activity)
        assertEquals(results, recent)
        assertEquals(1.5f, results.blurRadius)
        assertEquals(.20f, results.surfaceOpacity)
        assertTrue(results.surfaceOpacity > 0f && results.surfaceOpacity <= .40f / 2f)
        assertTrue(results.blurRadius < 3f)
        assertEquals(Color.Black, results.surfaceTintColor)
        // The complete optics recipe, fallback and rim come from the navbar.
        assertEquals(nav, results.copy(blurRadius = nav.blurRadius,
            surfaceOpacity = nav.surfaceOpacity, surfaceTintColor = nav.surfaceTintColor))
        assertTrue(results.lensSideEdges)
        controller.pause().stop().destroy()
    }

    @Test fun searchLensControlUsesTheSameRendererSwitchAsNavbar() {
        val intent = android.content.Intent().putExtra("probe_no_lens", true)
        val controller = Robolectric.buildActivity(Activity::class.java, intent).setup()
        val activity = controller.get()
        val results = searchResultsGlassConfig(activity)
        assertEquals(navigationGlassConfig(activity).lensHeight, results.lensHeight)
        assertEquals(navigationGlassConfig(activity).lensAmount, results.lensAmount)
        assertEquals(navigationGlassConfig(activity).centerMagnification, results.centerMagnification)
        assertEquals(results, recentSearchGlassConfig(activity))
        controller.pause().stop().destroy()
    }

    @Test fun headerUsesBlackFilmWithoutChangingNavbarOptics() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val nav = navigationGlassConfig(activity)
        val header = headerGlassConfig(activity)
        assertEquals(Color.Black, header.surfaceTintColor)
        assertFalse(header.lensSideEdges)
        assertTrue(nav.lensSideEdges)
        assertEquals(Color.Unspecified, nav.surfaceTintColor)
        // All other material values, including the platform fallback, stay shared.
        assertEquals(nav, header.copy(surfaceTintColor = nav.surfaceTintColor, lensSideEdges = true))
        controller.pause().stop().destroy()
    }
}
