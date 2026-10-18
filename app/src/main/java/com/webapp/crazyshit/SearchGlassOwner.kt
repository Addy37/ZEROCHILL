package com.webapp.crazyshit

import android.app.Activity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Standalone search uses the same legacy Activity bridge as the navbar. */
internal class SearchGlassOwner(activity: Activity) {
    private val owner = NavOwner(activity)
    init {
        activity.window.decorView.setViewTreeLifecycleOwner(owner)
        activity.window.decorView.setViewTreeSavedStateRegistryOwner(owner)
    }
    fun close() = owner.close()
}
