package com.webapp.crazyshit.ui

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webapp.crazyshit.R

/** Resource-backed roles shared with the approved View UI. No dynamic/system palette. */
@Composable
fun ZeroChillTheme(content: @Composable () -> Unit) {
    val oled = LocalContext.current.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getBoolean("oled_black_enabled", true)
    val accent = colorResource(R.color.zc_cyan)
    val colors = darkColorScheme(
        primary = accent, onPrimary = colorResource(R.color.app_on_primary),
        primaryContainer = colorResource(R.color.zc_cyan_container),
        onPrimaryContainer = colorResource(R.color.zc_text_primary),
        secondary = accent, onSecondary = colorResource(R.color.app_on_primary),
        secondaryContainer = colorResource(R.color.zc_surface_raised),
        onSecondaryContainer = colorResource(R.color.zc_text_primary),
        tertiary = accent, onTertiary = colorResource(R.color.app_on_primary),
        tertiaryContainer = colorResource(R.color.zc_surface_raised),
        onTertiaryContainer = colorResource(R.color.zc_text_primary),
        background = colorResource(if (oled) R.color.zc_background else R.color.app_background),
        onBackground = colorResource(R.color.zc_text_primary),
        surface = colorResource(R.color.zc_surface),
        surfaceDim = colorResource(R.color.zc_surface),
        surfaceBright = colorResource(R.color.zc_surface_floating),
        surfaceContainerLowest = colorResource(R.color.zc_background),
        surfaceContainerLow = colorResource(R.color.zc_surface),
        surfaceContainer = colorResource(R.color.zc_surface),
        surfaceContainerHigh = colorResource(R.color.zc_surface_raised),
        surfaceContainerHighest = colorResource(R.color.zc_surface_floating),
        onSurface = colorResource(R.color.zc_text_primary),
        surfaceVariant = colorResource(R.color.zc_surface_raised),
        onSurfaceVariant = colorResource(R.color.zc_text_secondary),
        outline = colorResource(R.color.zc_edge), outlineVariant = colorResource(R.color.zc_divider),
        error = colorResource(R.color.zc_destructive),
        onError = colorResource(R.color.app_on_primary),
        errorContainer = colorResource(R.color.zc_surface_raised),
        onErrorContainer = colorResource(R.color.zc_destructive),
        inverseSurface = colorResource(R.color.zc_text_primary),
        inverseOnSurface = colorResource(R.color.zc_background), inversePrimary = accent,
        surfaceTint = accent, scrim = colorResource(R.color.zc_scrim),
    )
    MaterialTheme(colorScheme = colors, typography = ZeroChillTypography,
        shapes = ZeroChillShapes, content = content)
}

private val ZeroChillTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

private val ZeroChillShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

object ZeroChillSpace {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val Section = 24.dp
    val Touch = 48.dp
}
