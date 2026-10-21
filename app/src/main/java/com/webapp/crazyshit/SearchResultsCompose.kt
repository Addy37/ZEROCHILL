package com.webapp.crazyshit

import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.ImageView
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.Glide
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.RecyclerView
import com.webapp.crazyshit.ui.ZeroChillTheme

/** Immutable presentation snapshots retain provider order while Compose owns result rows. */
internal class SearchResultsCompose(activity: Activity) {
    private val owner = NavOwner(activity)
    private val decor = activity.window.decorView
    private val installedWindowOwner = decor.findViewTreeLifecycleOwner() == null
    init {
        // WindowRecomposer is installed above the ComposeView in a legacy Activity.
        if (installedWindowOwner) {
            decor.setViewTreeLifecycleOwner(owner)
            decor.setViewTreeSavedStateRegistryOwner(owner)
        }
    }
    private data class ResultSnapshot(val key: String, val position: Int, val section: String?,
        val item: NativeContentItem?, val handle: String, val meta: String, val description: String,
        val generation: Int, val imageRevision: Int)
    private class ImageBinding { var request: List<Any>? = null }
    private var rows by mutableStateOf(emptyList<ResultSnapshot>())
    private var revision = 0
    private var sourceAdapter: RecyclerView.Adapter<*>? = null
    private val favorites = activity.getSharedPreferences("creator_favorites", Context.MODE_PRIVATE)
    private var favoriteRevision by mutableIntStateOf(0)
    private val favoritesChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        activity.runOnUiThread { favoriteRevision++ }
    }
    init { favorites.registerOnSharedPreferenceChangeListener(favoritesChanged) }
    private val scroll = LazyListState()
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = publish()
        override fun onItemRangeInserted(start: Int, count: Int) = publish()
        override fun onItemRangeRemoved(start: Int, count: Int) = publish()
        override fun onItemRangeChanged(start: Int, count: Int) = publish(start until start + count)
        override fun onItemRangeChanged(start: Int, count: Int, payload: Any?) = publish(start until start + count, payload)
        override fun onItemRangeMoved(from: Int, to: Int, count: Int) = publish()

    }
    val view = ComposeView(activity).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ZeroChillTheme {
                LazyColumn(Modifier.fillMaxSize(), state = scroll) {
                    items(rows, key = { it.key }, contentType = { if (it.section != null) "section" else "result" }) { row ->
                        if (row.section != null) Text(row.section, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
                                .semantics { heading() })
                        else ResultRow(row)
                    }
                }
            }
        }
    }

    fun setAdapter(value: RecyclerView.Adapter<out RecyclerView.ViewHolder>) {
        sourceAdapter?.unregisterAdapterDataObserver(observer)
        sourceAdapter = value
        value.registerAdapterDataObserver(observer)
        publish()
    }
    fun getAdapter(): RecyclerView.Adapter<*>? = sourceAdapter
    fun getItemCount() = rows.size
    private fun keyAt(position: Int): String = when (val source = sourceAdapter) {
        is GlobalSearchAdapter -> source.composeKey(position)
        is OnlyFapCreatorSearchAdapter -> source.composeKey(position)
        else -> position.toString()
    }
    private fun publish(changed: IntRange? = null, payload: Any? = null) {
        val source = sourceAdapter ?: return
        val previous = rows.associateBy { it.key }
        // DiffUtil dispatches several events after replacing its whole backing list. Read all
        // row content now, rather than binding a later frame through intermediate positions.
        rows = List(source.itemCount) { index ->
            val key = keyAt(index)
            val old = previous[key]
            val imageRevision = if (payload == "thumbnail" && changed?.contains(index) == true)
                ++revision else old?.imageRevision ?: 0
            when (source) {
                is GlobalSearchAdapter -> {
                    val entry = source.composeEntry(index)
                    ResultSnapshot(key, index, if (entry.section) entry.sectionTitle else null, entry.item, "",
                        if (entry.section) "" else source.composeMeta(entry), entry.item?.title ?: "",
                        source.composeGeneration(), imageRevision)
                }
                is OnlyFapCreatorSearchAdapter -> {
                    val item = source.composeItem(index)
                    ResultSnapshot(key, index, null, item, source.composeHandle(item), source.composeMeta(item),
                        "${item.title}, OnlyFap creator", source.composeGeneration(), imageRevision)
                }
                else -> error("Unsupported Search presentation source")
            }
        }
    }

    @Composable private fun ResultRow(row: ResultSnapshot) {
        val item = row.item ?: return
        val source = sourceAdapter
        val context = LocalContext.current
        val alpha = remember(source, row.key, row.generation) {
            val first = when (source) {
                is GlobalSearchAdapter -> source.composeFirstPresentation(row.key)
                is OnlyFapCreatorSearchAdapter -> source.composeFirstPresentation(row.key)
                else -> false
            }
            Animatable(if (first && ZeroChillMotion.animationsEnabled(context)) 0f else 1f)
        }
        LaunchedEffect(alpha) {
            alpha.animateTo(1f, tween(180, easing = Easing { ZeroChillMotion.EASE_OUT.getInterpolation(it) }))
        }
        DisposableEffect(source, item.url, item.searchQuery, row.generation) {
            if (item.isCreator) CreatorGalleryPreloader.warm(context, item,
                if (row.position < (if (source is OnlyFapCreatorSearchAdapter) 3 else 6))
                    CreatorGalleryPreloader.PRIORITY_HIGH else CreatorGalleryPreloader.PRIORITY_NORMAL)
            onDispose { if (source is OnlyFapCreatorSearchAdapter && item.isCreator) CreatorGalleryPreloader.cancelQueued(item) }
        }
        val saved = if (item.isCreator) remember(favoriteRevision, item.searchQuery, item.title) {
            CreatorFavoriteStore.contains(context, item)
        } else false
        fun toggleFavorite() { CreatorFavoriteStore.toggle(context, item) }
        Column(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha.value }
            .combinedClickable(onClick = {
                when (source) {
                    is GlobalSearchAdapter -> source.composeOpen(item)
                    is OnlyFapCreatorSearchAdapter -> source.composeOpen(item)
                }
            }, onLongClick = if (item.isCreator) ({ toggleFavorite() }) else null)
            .semantics { contentDescription = row.description }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 104.dp)
                .padding(start = if (source is OnlyFapCreatorSearchAdapter) 10.dp else 12.dp, end = 8.dp,
                    top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                val binding = remember(source, row.key) { ImageBinding() }
                AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                    update = { image ->
                        val request = listOf(item.url, item.kind, item.imageUrl, item.uploader, row.imageRevision)
                        if (binding.request != request) {
                            binding.request = request
                            when (source) {
                                is GlobalSearchAdapter -> source.composeLoadImage(image, item)
                                is OnlyFapCreatorSearchAdapter -> source.composeLoadImage(image, item)
                            }
                        }
                    }, onRelease = { Glide.with(it).clear(it) },
                    modifier = Modifier.size(if (item.isCreator) 64.dp else 88.dp, if (item.isCreator) 64.dp else 66.dp)
                        .clip(if (item.isCreator) CircleShape else RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F1F24)))
                Column(Modifier.weight(1f).padding(start = if (source is OnlyFapCreatorSearchAdapter) 11.dp else 12.dp, end = 8.dp)) {
                    Text(item.title, color = Color.White, fontSize = 18.sp, lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (row.handle.isNotEmpty()) Text(row.handle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.meta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                if (item.isCreator) IconButton(onClick = { toggleFavorite() }, modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = (if (saved) "Unfavorite " else "Favorite ") + item.title }) {
                    Text(if (saved) "★" else "☆", fontSize = 25.sp,
                        color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 12.dp), thickness = .5.dp, color = Color(0x16FFFFFF))
        }
    }
    fun canScrollForward() = scroll.canScrollForward
    fun scrollToPosition(position: Int) { scroll.requestScrollToItem(position) }
    fun saveScroll(): Parcelable = Bundle().apply {
        putInt("compose_index", scroll.firstVisibleItemIndex)
        putInt("compose_offset", scroll.firstVisibleItemScrollOffset)
    }
    fun restoreScroll(value: Parcelable?) {
        val state = value as? Bundle ?: return
        scroll.requestScrollToItem(state.getInt("compose_index"), state.getInt("compose_offset"))
    }
    fun close() {
        sourceAdapter?.unregisterAdapterDataObserver(observer)
        favorites.unregisterOnSharedPreferenceChangeListener(favoritesChanged)
        view.disposeComposition()
        if (installedWindowOwner && decor.findViewTreeLifecycleOwner() === owner) {
            decor.setViewTreeLifecycleOwner(null)
            decor.setViewTreeSavedStateRegistryOwner(null)
        }
        owner.close()
    }
}
