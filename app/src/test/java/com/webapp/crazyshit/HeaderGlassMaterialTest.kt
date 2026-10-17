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
