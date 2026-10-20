package com.addy37.crazyshitadmin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ElectricBlue = Color(0xFF0892D0)
internal val Ink = Color(0xFF030609)
internal val Panel = Color(0xFF0D141B)
internal val Muted = Color(0xFF9AAEBE)
internal val Danger = Color(0xFFFF8491)
internal val Good = Color(0xFF96E3BE)
internal val ConsoleShape = RoundedCornerShape(22.dp)

@Composable internal fun ConsoleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = ElectricBlue, onPrimary = Ink, background = Ink, onBackground = Color(0xFFF1F7FC),
            surface = Panel, onSurface = Color(0xFFF1F7FC), surfaceVariant = Color(0xFF14212B), onSurfaceVariant = Muted,
            secondary = Good, error = Danger, outline = Color(0xFF31424E), outlineVariant = Color(0xFF233440),
            primaryContainer = Color(0xFF12343D), onPrimaryContainer = ElectricBlue,
            secondaryContainer = Color(0xFF12343D), onSecondaryContainer = ElectricBlue,
            tertiary = ElectricBlue, tertiaryContainer = Panel, onTertiaryContainer = ElectricBlue,
            inverseSurface = Color(0xFF16313C), inverseOnSurface = Color(0xFFF1F7FC), inversePrimary = ElectricBlue,
            surfaceDim = Ink, surfaceBright = Color(0xFF1B2B35),
            surfaceContainerLowest = Ink, surfaceContainerLow = Color(0xFF091017), surfaceContainer = Panel,
            surfaceContainerHigh = Color(0xFF111D26), surfaceContainerHighest = Color(0xFF172630)),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 64.sp, letterSpacing = (-3).sp),
            headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp)),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = ConsoleShape),
        content = {
            Surface(Modifier.fillMaxSize(), color = Ink, contentColor = MaterialTheme.colorScheme.onBackground, content = content)
        }
    )
}
@Composable internal fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = color)
}
@Composable internal fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ZERO", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
        Text("CHILL", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp, color = ElectricBlue)
        Text(" / ADMIN", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
@Composable internal fun ConsolePanel(modifier: Modifier = Modifier, accent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(ConsoleShape).background(if(accent) Brush.linearGradient(listOf(Color(0xFF092C36), Panel))
        else Brush.linearGradient(listOf(Panel, Color(0xFF091016)))).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable internal fun StatusTag(text: String, color: Color = ElectricBlue) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.09f)).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.size(5.dp)) { drawCircle(color) }
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
@Composable internal fun ActionIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled) { Icon(icon, contentDescription = description, tint = if(enabled) Muted else Muted.copy(alpha = .4f)) }
}
@Composable internal fun BusyLine(show: Boolean) {
    if(show) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = ElectricBlue, trackColor = Panel)
    else Spacer(Modifier.height(2.dp))
}
@Composable internal fun ErrorPanel(message: String?, retry: (() -> Unit)? = null) {
    if (message == null) return
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Danger.copy(alpha=.09f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Danger)
        Text(message, Modifier.weight(1f), color = Danger, style = MaterialTheme.typography.bodyMedium)
        if(retry != null) TextButton(onClick = retry) { Text("Retry") }
    }
}
@Composable internal fun EmptyPanel(title: String, detail: String, icon: ImageVector = Icons.Outlined.Inbox) {
    Column(Modifier.fillMaxWidth().padding(vertical=36.dp, horizontal=24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = ElectricBlue, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable internal fun InitialLoad(label: String) {
    Column(Modifier.fillMaxWidth().padding(vertical=28.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Eyebrow(label)
        repeat(3) { index ->
            Column(Modifier.fillMaxWidth().height(92.dp).clip(ConsoleShape).background(Panel).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(.28f + index * .08f).height(10.dp).clip(ConsoleShape).background(Muted.copy(alpha=.12f)))
                Box(Modifier.fillMaxWidth(.7f).height(18.dp).clip(ConsoleShape).background(Muted.copy(alpha=.07f)))
            }
        }
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = ElectricBlue)
    }
}
internal data class Destination(val id: String, val label: String, val icon: ImageVector)
internal val destinations = listOf(Destination("home", "Home", Icons.Outlined.SpaceDashboard),
    Destination("feedback", "Feedback", Icons.Outlined.Forum), Destination("sources", "Sources", Icons.Outlined.Hub),
    Destination("analytics", "Analytics", Icons.Outlined.BarChart))
@Composable internal fun ConsoleNav(selected: String, unread: Int, navigate: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Ink).navigationBarsPadding().padding(horizontal=12.dp, vertical=8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        destinations.forEach { item ->
            val active = item.id == selected
            val color by animateColorAsState(if(active) ElectricBlue else Muted, tween(160), label="navigation")
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { navigate(item.id) }
                .padding(vertical=9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(5.dp)) {
                BadgedBox(badge={ if(item.id == "feedback" && unread > 0) Badge(containerColor=ElectricBlue, contentColor=Ink) { Text(unread.toString()) } }) {
                    Icon(item.icon, null, tint = color, modifier=Modifier.size(23.dp))
                }
                Text(item.label, color=color, fontSize=12.sp, fontWeight=if(active) FontWeight.Bold else FontWeight.Normal)
                Box(Modifier.width(if(active) 18.dp else 0.dp).height(2.dp).background(ElectricBlue))
            }
        }
    }
}
@Composable internal fun Metric(value: Long?, label: String, large: Boolean = false, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(value?.toFloat() ?: 0f, tween(350), label="metric")
    Column(modifier, verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(if(value == null) "···" else number(animated.toLong()), color=if(large) ElectricBlue else MaterialTheme.colorScheme.onSurface,
            style=if(large) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge)
        Text(label, color=Muted, fontSize=13.sp)
    }
}
@Composable internal fun EnergyField(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = ElectricBlue.copy(alpha=.10f)
        for(i in 0..8) {
            val x = size.width * i / 8f
            drawLine(c, Offset(x, 0f), Offset(x - size.height*.6f, size.height), 1f)
        }
        for(i in 0..5) drawLine(c, Offset(0f, size.height*i/5), Offset(size.width, size.height*i/5), 1f)
        drawCircle(Brush.radialGradient(listOf(ElectricBlue.copy(alpha=.17f), Color.Transparent), center=Offset(size.width*.8f, size.height*.2f), radius=size.width*.7f), radius=size.width*.7f, center=Offset(size.width*.8f,size.height*.2f))
    }
}
