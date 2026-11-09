package com.webapp.crazyshit

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Compose presentation only. Existing app_prefs and OG glass renderers stay authoritative. */
object AppearanceComposeScreen {
    private val Black = Color(0xFF000000)
    private val Surface = Color(0xFF101216)
    private val Raised = Color(0xFF1B2027)
    private val Stroke = Color(0xFF303841)
    private val Ink = Color(0xFFF5F7FA)
    private val Secondary = Color(0xFFB8C0CC)
    private val Muted = Color(0xFF929DAC)

    @JvmStatic
    fun mount(activity: AppearanceActivity) {
        val compose = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { AppearancePage(activity) }
        }
        activity.setContentView(compose)
    }

    private data class State(
        val accent: AppearanceTheme.Accent,
        val preset: AppearanceTheme.GlassPreset,
        val strength: Float,
        val tint: Float,
        val transparency: Float,
        val blur: Float,
        val refraction: Float,
        val vibrancy: Float,
        val edge: Float,
        val splitting: Boolean,
        val motion: Boolean,
    ) {
        companion object {
            fun read(activity: AppearanceActivity) = State(
                accent = AppearanceTheme.accentChoice(activity),
                preset = AppearanceTheme.glassPreset(activity),
                strength = AppearanceTheme.glassIntensity(activity),
                tint = AppearanceTheme.menuTint(activity),
                transparency = AppearanceTheme.glassTransparency(activity),
                blur = AppearanceTheme.glassBlur(activity),
                refraction = AppearanceTheme.glassRefraction(activity),
                vibrancy = AppearanceTheme.glassVibrancy(activity),
                edge = AppearanceTheme.glassEdge(activity),
                splitting = AppearanceTheme.glassColorSplitting(activity),
                motion = AppearanceTheme.prefs(activity).getBoolean("immersive_motion_enabled", true),
            )
        }
    }

    @Composable
    private fun AppearancePage(activity: AppearanceActivity) {
        var state by remember { mutableStateOf(State.read(activity)) }
        var resetOpen by remember { mutableStateOf(false) }
        val accent by animateColorAsState(Color(state.accent.color), tween(220), label = "accent")
        fun refresh() { state = State.read(activity) }

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = accent,
                onPrimary = Black,
                background = Black,
                onBackground = Ink,
                surface = Surface,
                onSurface = Ink,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(Black)
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                            .clickable { activity.finish() },
                        contentAlignment = Alignment.Center,
                    ) { Text("‹", fontSize = 34.sp, color = accent) }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Appearance", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("OLED BLACK  /  ZEROCHILL", fontSize = 11.sp, letterSpacing = 1.sp, color = Muted)
                    }
                }

                Panel {
                    Heading("Colors", "OLED black stays fixed. Pick your accent.")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        AppearanceTheme.Accent.values().forEach { choice ->
                            val selected = state.accent == choice
                            val color = Color(choice.color)
                            Box(
                                modifier = Modifier.size(43.dp).clip(RoundedCornerShape(15.dp))
                                    .background(Raised)
                                    .border(
                                        BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else Stroke),
                                        RoundedCornerShape(15.dp),
                                    )
                                    .semantics { contentDescription = choice.label + if (selected) ", selected" else "" }
                                    .clickable {
                                        AppearanceTheme.setAccent(activity, choice)
                                        AppearanceTheme.applyRuntimeTheme(activity)
                                        refresh()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(24.dp).background(color, CircleShape))
                                if (selected) Text("✓", color = Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Preview(state, accent)
                }

                Panel {
                    Heading("Liquid glass", "Fine-tune the existing OG effects")
                    listOf(
                        listOf(AppearanceTheme.GlassPreset.OFF, AppearanceTheme.GlassPreset.FAST),
                        listOf(AppearanceTheme.GlassPreset.BALANCED, AppearanceTheme.GlassPreset.QUALITY),
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { preset ->
                                val selected = state.preset == preset
                                OutlinedButton(
                                    onClick = {
                                        AppearanceTheme.setGlassPreset(activity, preset)
                                        refresh()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, if (selected) accent else Stroke),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) accent.copy(alpha = .16f) else Raised,
                                        contentColor = if (selected) accent else Secondary,
                                    ),
                                    modifier = Modifier.weight(1f).height(43.dp),
                                ) {
                                    Text(preset.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    GlassSlider("Optical strength", "Overall optical effect", state.strength, 0f..1f, "%", accent) {
                        AppearanceTheme.setGlassIntensity(activity, it)
                        refresh()
                    }
                    GlassSlider("Menu tint", "Dark coating on native menus", state.tint, 0f..1f, "%", accent) {
                        AppearanceTheme.setMenuTint(activity, it)
                        refresh()
                    }
                    GlassSlider("Transparency", "Content showing through glass", state.transparency, 0f..1f, "%", accent) {
                        AppearanceTheme.setGlassFloat(activity, AppearanceTheme.KEY_GLASS_TRANSPARENCY, it)
                        refresh()
                    }
                    GlassSlider("Blur", "Frost behind the surface", state.blur, 0f..12f, "dp", accent) {
                        AppearanceTheme.setGlassFloat(activity, AppearanceTheme.KEY_GLASS_BLUR, it)
                        refresh()
                    }
                    GlassSlider("Refraction", "Bend the image at glass edges", state.refraction, 0f..30f, "dp", accent) {
                        AppearanceTheme.setGlassFloat(activity, AppearanceTheme.KEY_GLASS_REFRACTION, it)
                        refresh()
                    }
                    GlassSlider("Vibrancy", "Color passing through glass", state.vibrancy, 1f..2f, "×", accent) {
                        AppearanceTheme.setGlassFloat(activity, AppearanceTheme.KEY_GLASS_VIBRANCY, it)
                        refresh()
                    }
                    GlassSlider("Edge highlights", "Brightness of the reflective rim", state.edge, 0f..1f, "%", accent) {
                        AppearanceTheme.setGlassFloat(activity, AppearanceTheme.KEY_GLASS_EDGE, it)
                        refresh()
                    }
                    Toggle("Color splitting", "Rainbow edges on Android 13+", state.splitting,
                        Build.VERSION.SDK_INT >= 33, accent) {
                        AppearanceTheme.setGlassColorSplitting(activity, it)
                        refresh()
                    }
                }

                Panel {
                    Heading("Motion", "Customize animations")
                    Toggle("Animations", "Respects Android reduced motion", state.motion, true, accent) {
                        AppearanceTheme.prefs(activity).edit()
                            .putBoolean("immersive_motion_enabled", it).apply()
                        refresh()
                    }
                }

                TextButton(
                    onClick = { resetOpen = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Reset appearance to defaults", color = accent, fontSize = 13.sp) }
                Spacer(Modifier.height(12.dp))
            }

            if (resetOpen) AlertDialog(
                onDismissRequest = { resetOpen = false },
                containerColor = Raised,
                titleContentColor = Ink,
                textContentColor = Secondary,
                title = { Text("Reset appearance?") },
                text = { Text("Restore cyan and default glass settings? Content, downloads and motion choices stay untouched.") },
                confirmButton = {
                    TextButton(onClick = {
                        AppearanceTheme.reset(activity)
                        AppearanceTheme.applyRuntimeTheme(activity)
                        refresh()
                        resetOpen = false
                    }) { Text("Reset", color = accent) }
                },
                dismissButton = {
                    TextButton(onClick = { resetOpen = false }) { Text("Cancel", color = Secondary) }
                },
            )
        }
    }

    @Composable
    private fun Panel(content: @Composable ColumnScope.() -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = BorderStroke(1.dp, Stroke),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                content = content,
            )
        }
    }

    @Composable
    private fun Heading(title: String, detail: String) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(detail, fontSize = 12.sp, color = Muted)
    }

    @Composable
    private fun Preview(state: State, accent: Color) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1D2632), Color(0xFF18252B), Surface)))
                .border(1.dp, Stroke, RoundedCornerShape(17.dp))
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("LIVE PREVIEW", fontSize = 10.sp, letterSpacing = 1.sp, color = Secondary)
                Row {
                    listOf("Shows", "ShitTok", "OnlyFap", "Library").forEachIndexed { index, label ->
                        Text(
                            label, fontSize = 10.sp,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == 0) accent else Secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = .05f + state.strength * .13f),
                                    Surface.copy(alpha = .32f + state.tint * .42f),
                                    accent.copy(alpha = .04f + state.edge * .12f),
                                ),
                            ),
                        )
                        .border(
                            1.dp, accent.copy(alpha = .2f + state.edge * .65f),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) { Text("Sample glass control", fontSize = 12.sp, color = Ink) }
            }
        }
    }

    @Composable
    private fun GlassSlider(
        title: String,
        detail: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        unit: String,
        accent: Color,
        onChange: (Float) -> Unit,
    ) {
        val label = if (unit == "%") (value * 100f).roundToInt().toString() + "%"
            else ( (value * 10f).roundToInt() / 10f ).toString() + " " + unit
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                Text(label, fontSize = 12.sp, color = accent)
            }
            Text(detail, fontSize = 11.sp, color = Muted)
            Slider(
                value = value, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Raised,
                ),
                modifier = Modifier.fillMaxWidth().height(36.dp),
            )
        }
    }

    @Composable
    private fun Toggle(
        title: String, detail: String, checked: Boolean, enabled: Boolean,
        accent: Color, onChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = Ink)
                Text(detail, fontSize = 11.sp, color = Muted)
            }
            Switch(
                checked = checked, onCheckedChange = onChange, enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Black, checkedTrackColor = accent,
                    uncheckedThumbColor = Secondary, uncheckedTrackColor = Raised,
                ),
            )
        }
    }
}
