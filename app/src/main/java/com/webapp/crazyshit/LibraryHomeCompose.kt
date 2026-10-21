package com.webapp.crazyshit

import android.app.Activity
import android.view.View
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import com.webapp.crazyshit.ui.ZeroChillTheme
import java.util.function.BiConsumer

/** Compose owns vertical layout/scroll; the account header and bounded media rails stay native. */
internal class LibraryHomeCompose(activity: Activity, host: View, content: View) {
    private val owner = NavOwner(activity)
    private val scroll = ScrollState(0)
    var restoredPosition by mutableIntStateOf(-1)
    fun scrollPosition() = scroll.value
    init {
        host.setViewTreeLifecycleOwner(owner)
        host.setViewTreeSavedStateRegistryOwner(owner)
    }
    var onScroll: BiConsumer<Int, Boolean>? = null
    val view = ComposeView(activity).apply {
        id = R.id.zerochill_library_content
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ZeroChillTheme {
                LaunchedEffect(restoredPosition) {
                    if (restoredPosition >= 0) { scroll.scrollTo(restoredPosition); restoredPosition = -1 }
                }
                LaunchedEffect(scroll) {
                    var previous = scroll.value
                    snapshotFlow { scroll.value }.collect { value ->
                        onScroll?.accept(value - previous, value <= (4 * activity.resources.displayMetrics.density).toInt())
                        previous = value
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    AndroidView(factory = { content }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    fun close() { view.disposeComposition(); owner.close() }
}
