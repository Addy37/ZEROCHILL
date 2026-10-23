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
    @Test fun resultsSoftenTheBackdropWithoutChangingOtherGlassOptics() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val header = headerGlassConfig(activity)
        val results = searchResultsGlassConfig(activity)
        assertEquals(12f, results.blurRadius)
        assertEquals(.40f, results.surfaceOpacity)
        assertEquals(Color.Black, results.surfaceTintColor)
        assertEquals(header, results.copy(blurRadius = header.blurRadius,
            surfaceOpacity = header.surfaceOpacity))
        controller.pause().stop().destroy()
    }

    @Test fun recentSearchesKeepTheSharedLensAndDarkFilmWithLessObscuringBlur() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val recent = recentSearchGlassConfig(activity)
        val nav = navigationGlassConfig(activity)
        assertEquals(3f, recent.blurRadius)
        assertEquals(.40f, recent.surfaceOpacity)
        assertEquals(Color.Black, recent.surfaceTintColor)
        assertEquals(nav.lensHeight, recent.lensHeight)
        assertEquals(nav.lensAmount, recent.lensAmount)
        assertEquals(nav.centerMagnification, recent.centerMagnification)
        assertEquals(searchResultsGlassConfig(activity), recent.copy(blurRadius = 12f))
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
