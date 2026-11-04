package com.addy37.zerochill.v2preview

import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val Accent = Color(0xFF0892D0)
private val White = Color(0xFFF5F7FA)
private val Gray = Color(0xFFB8C0CC)
private val Panel = Color(0xFF15191F)
private val Outline = Color(0xFF343E49)

private data class Tab(val title: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("Shows", Icons.Filled.Movie),
    Tab("ShitTok", Icons.Filled.PlayArrow),
    Tab("OnlyFap", Icons.Filled.GridView),
    Tab("Library", Icons.Filled.Person),
)

@Composable
fun ZeroChillSkeleton() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var subpage by rememberSaveable { mutableStateOf<String?>(null) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Color.Black,
            onBackground = White,
            surface = Panel,
            onSurface = White,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .statusBarsPadding().navigationBarsPadding(),
        ) {
            BrandHeader()
            Box(Modifier.weight(1f)) {
                when {
                    subpage != null -> DetailPlaceholder(subpage!!) { subpage = null }
                    selected == 0 -> ShowsPlaceholder()
                    selected == 1 -> ShitTokPlaceholder()
                    selected == 2 -> OnlyFapPlaceholder()
                    else -> LibraryPlaceholder { subpage = it }
                }
            }
            GlassNavigation(selected) {
                selected = it
                subpage = null
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The existing native layer-list drawable preserves the real mascot artwork.
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_zerochill_devil)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "ZEROCHILL devil mascot"
                }
            },
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Row {
                Text("ZERO", color = White, fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text("CHILL", color = Accent, fontWeight = FontWeight.Black, fontSize = 21.sp)
            }
            Text(
                "NO LIMITS. ALL CONTENT.",
                color = Gray,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Text("V2 PREVIEW", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlassNavigation(selected: Int, onSelected: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(29.dp),
        color = Panel,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
    ) {
        Row(Modifier.height(64.dp).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, tab ->
                val active = selected == index
                val tint = if (active) Accent else Gray
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (active) Accent.copy(alpha = 0.13f) else Color.Transparent)
                        .clickable { onSelected(index) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.title, tint = tint, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(tab.title, color = tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
        if (subtitle != null) Text(subtitle, color = Gray, fontSize = 12.sp)
    }
}

@Composable
private fun ArtworkPlaceholder(
    name: String,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
) {
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier.clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF202A35), Color(0xFF0B121A), Color(0xFF17191D)),
                ),
            )
            .border(1.dp, Outline.copy(alpha = 0.75f), shape),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (tall) Icons.Filled.Person else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Accent.copy(alpha = 0.55f),
                modifier = Modifier.size(35.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("PREVIEW", color = Gray, fontSize = 10.sp, letterSpacing = 1.4.sp)
        }
        Text(
            name,
            color = White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(13.dp),
        )
    }
}

@Composable
private fun ShowsPlaceholder() {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionTitle("Shows", "Sample layout. No providers connected.") }
        item { ArtworkPlaceholder("Featured show", Modifier.fillMaxWidth().height(212.dp)) }
        item { Text("Continue Watching", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArtworkPlaceholder("Episode 01", Modifier.weight(1f).height(126.dp))
                ArtworkPlaceholder("Episode 02", Modifier.weight(1f).height(126.dp))
            }
        }
        item { Text("Explore", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArtworkPlaceholder("Category A", Modifier.weight(1f).height(175.dp), tall = true)
                ArtworkPlaceholder("Category B", Modifier.weight(1f).height(175.dp), tall = true)
            }
        }
    }
}

@Composable
private fun ShitTokPlaceholder() {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF192735), Color(0xFF0C1018), Color(0xFF050506)),
                ),
            )
            .border(1.dp, Outline, shape),
    ) {
        Row(Modifier.align(Alignment.TopCenter).padding(top = 18.dp)) {
            Text("Following", color = Gray, fontSize = 14.sp)
            Spacer(Modifier.width(18.dp))
            Text("For You", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Accent, modifier = Modifier.size(58.dp))
            Text("VIDEO PLACEHOLDER", color = Gray, fontSize = 12.sp)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 23.dp)) {
            Text("@zerochill_preview", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Swipe feed layout preview", color = Gray, fontSize = 12.sp)
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 74.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = "Like placeholder", tint = White, modifier = Modifier.size(29.dp))
            Icon(Icons.Filled.MoreHoriz, contentDescription = "More placeholder", tint = White, modifier = Modifier.size(29.dp))
        }
    }
}

@Composable
private fun OnlyFapPlaceholder() {
    val sample = listOf("Creator 01", "Creator 02", "Creator 03", "Creator 04", "Creator 05", "Creator 06")
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            SectionTitle("OnlyFap", "Sample creators and gallery layout.")
        }
        item(span = { GridItemSpan(2) }) {
            Surface(
                color = Panel, shape = RoundedCornerShape(23.dp),
                border = BorderStroke(1.dp, Outline),
            ) {
                Text(
                    "All    Female    Male    Trans",
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    color = Gray,
                    fontSize = 12.sp,
                )
            }
        }
        items(sample) { title ->
            ArtworkPlaceholder(title, Modifier.fillMaxWidth().height(205.dp), tall = true)
        }
    }
}

@Composable
private fun LibraryPlaceholder(open: (String) -> Unit) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item { SectionTitle("Library", "Your personal space, shown with placeholders.") }
        item {
            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Accent, modifier = Modifier.size(37.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Your profile", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Account features come later", color = Gray, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryTile("Favorites", Modifier.weight(1f), open)
                LibraryTile("History", Modifier.weight(1f), open)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryTile("Downloads", Modifier.weight(1f), open)
                LibraryTile("Watch Later", Modifier.weight(1f), open)
            }
        }
        item { LibraryTile("More / Settings", Modifier.fillMaxWidth(), open) }
    }
}

@Composable
private fun LibraryTile(title: String, modifier: Modifier, onClick: (String) -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick(title) },
        shape = RoundedCornerShape(21.dp),
        color = Panel,
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Icon(
                if (title == "More / Settings") Icons.Filled.Settings else Icons.Filled.Favorite,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(23.dp),
            )
            Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Preview only", color = Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        color = Panel, shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), content = content)
    }
}

@Composable
private fun DetailPlaceholder(title: String, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = back).padding(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Accent)
            Spacer(Modifier.width(9.dp))
            Text("Back to Library", color = Gray, fontSize = 14.sp)
        }
        SectionTitle(title, "Design placeholder")
        GlassPanel {
            Text("Coming later", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "This preview has no live data, account access, downloads, or online features.",
                color = Gray, fontSize = 14.sp,
            )
        }
    }
}
