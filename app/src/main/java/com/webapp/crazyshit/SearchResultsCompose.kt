package com.webapp.crazyshit

import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.FrameLayout
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

/** Lazy Compose viewport around the approved row binders and their per-query fade policy. */
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
    private data class Row(val key: String, val position: Int, val type: Int, val revision: Int, val payload: Any? = null)
    private class Binding(val holder: RecyclerView.ViewHolder, var revision: Int = -1)
    private var rows by mutableStateOf(emptyList<Row>())
    private var revision = 0
    private var sourceAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = null
    private val scroll = LazyListState()
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = publish(allChanged = true)
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
                    items(rows, key = { it.key }, contentType = { it.type }) { row ->
                        val source = sourceAdapter ?: return@items
                        val binding = remember(row.key, row.type) {
                            Binding(source.createViewHolder(FrameLayout(activity), row.type))
                        }
                        val holder = binding.holder
                        AndroidView(factory = {
                            FrameLayout(activity).apply {
                                addView(holder.itemView, FrameLayout.LayoutParams(-1, -2))
                            }
                        }, update = {
                            // An async provider can replace the backing snapshot before recomposition.
                            if (row.position < source.itemCount && keyAt(row.position) == row.key && binding.revision != row.revision) {
                                if (row.payload != null && binding.revision >= 0)
                                    source.onBindViewHolder(holder, row.position, listOf(row.payload))
                                else source.bindViewHolder(holder, row.position)
                                binding.revision = row.revision
                            }
                        }, onRelease = { source.onViewRecycled(holder) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun setAdapter(value: RecyclerView.Adapter<out RecyclerView.ViewHolder>) {
        sourceAdapter?.unregisterAdapterDataObserver(observer)
        sourceAdapter = value as RecyclerView.Adapter<RecyclerView.ViewHolder>
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
    private fun publish(changed: IntRange? = null, payload: Any? = null, allChanged: Boolean = false) {
        val source = sourceAdapter ?: return
        val previous = rows.associateBy { it.key }
        rows = List(source.itemCount) { index ->
            val key = keyAt(index)
            val old = previous[key]
            val affected = allChanged || changed?.contains(index) == true || old == null
            Row(key, index, source.getItemViewType(index), if (affected) ++revision else old!!.revision,
                if (affected) payload else old?.payload)
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
        view.disposeComposition()
        if (installedWindowOwner && decor.findViewTreeLifecycleOwner() === owner) {
            decor.setViewTreeLifecycleOwner(null)
            decor.setViewTreeSavedStateRegistryOwner(null)
        }
        owner.close()
    }
}
