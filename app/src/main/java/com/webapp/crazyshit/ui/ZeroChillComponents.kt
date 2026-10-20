package com.webapp.crazyshit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webapp.crazyshit.R
import com.webapp.crazyshit.glass.GlassEffectConfig
import com.webapp.crazyshit.glass.backdrop.Backdrop
import com.webapp.crazyshit.glass.liquidGlass

/** Bounded phone/tablet content, with system/cutout/keyboard protection applied once. */
@Composable
fun ZeroChillScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(ZeroChillSpace.Touch)) {
                        Icon(painterResource(R.drawable.ic_player_back), "Back")
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 8.dp).semantics { heading() })
                }
                content()
            }
        }
    }
}

/** Static panels avoid capturing a black page. Over-media callers reuse the existing glass engine. */
@Composable
fun ZeroChillPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(content = content)
    }
}

@Composable
fun ZeroChillGlassSurface(backdrop: Backdrop, config: GlassEffectConfig,
    modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.liquidGlass(config, MaterialTheme.shapes.medium, ownBackdrop = backdrop), content = content)
}

@Composable
fun ZeroChillSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
private fun RowCopy(title: String, subtitle: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ZeroChillActionRow(title: String, subtitle: String = "", value: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Stack long values with copy, so large system fonts don't squeeze the title away.
        RowCopy(title, subtitle, Modifier.weight(1f))
        if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 120.dp))
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ZeroChillToggleRow(title: String, subtitle: String = "", checked: Boolean,
    enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch,
        onValueChange = onCheckedChange).heightIn(min = 60.dp)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RowCopy(title, subtitle, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun ZeroChillButton(label: String, enabled: Boolean = true, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = ZeroChillSpace.Touch), enabled = enabled,
        shape = MaterialTheme.shapes.small) { Text(label, textAlign = TextAlign.Center) }
}

@Composable
fun ZeroChillLoading(label: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ZeroChillEmptyState(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) ZeroChillButton(action, onClick = onAction)
    }
}
