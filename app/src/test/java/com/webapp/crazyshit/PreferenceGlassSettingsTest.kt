package com.webapp.crazyshit

import android.app.Application
import org.robolectric.RuntimeEnvironment
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PreferenceGlassSettingsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before fun setup() { AppearanceTheme.prefs(context).edit().clear().commit() }
    @After fun cleanup() { AppearanceTheme.prefs(context).edit().clear().commit() }

    @Test fun offAndFastAvoidOpticalBackdropWhileQualityRestoresIt() {
        assertTrue(PreferenceGlassPanel.usesOpticalLayer(context))
        AppearanceTheme.setGlassPreset(context, AppearanceTheme.GlassPreset.OFF)
        assertFalse(PreferenceGlassPanel.usesOpticalLayer(context))
        assertEquals(0f, PreferenceGlassPanel.edgeStrength(context), 0f)
        AppearanceTheme.setGlassPreset(context, AppearanceTheme.GlassPreset.FAST)
        assertFalse(PreferenceGlassPanel.usesOpticalLayer(context))
        assertTrue(PreferenceGlassPanel.edgeStrength(context) > 0f)
        AppearanceTheme.setGlassPreset(context, AppearanceTheme.GlassPreset.QUALITY)
        assertTrue(PreferenceGlassPanel.usesOpticalLayer(context))
        assertTrue(PreferenceGlassPanel.edgeStrength(context) > 1f)
    }

    @Test fun customEdgeZeroSuppressesRimsWithoutDisablingCustomOptics() {
        AppearanceTheme.setGlassFloat(context, AppearanceTheme.KEY_GLASS_EDGE, 0f)
        assertEquals(0f, PreferenceGlassPanel.edgeStrength(context), 0f)
        assertTrue(PreferenceGlassPanel.usesOpticalLayer(context))
        AppearanceTheme.setGlassFloat(context, AppearanceTheme.KEY_GLASS_EDGE, .35f)
        assertEquals(.5f, PreferenceGlassPanel.edgeStrength(context), .001f)
        AppearanceTheme.reset(context)
        assertEquals(1f, PreferenceGlassPanel.edgeStrength(context), 0f)
        assertTrue(PreferenceGlassPanel.usesOpticalLayer(context))
    }
}
