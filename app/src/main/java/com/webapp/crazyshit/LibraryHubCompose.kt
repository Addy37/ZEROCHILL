package com.webapp.crazyshit

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.webapp.crazyshit.ui.*

internal object LibraryHubCompose {
    @JvmStatic fun show(activity: LibraryHubActivity) {
        activity.installZeroChillContent {
            ZeroChillScreen("Library", activity::finish) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Your saved viewing and downloads", Modifier.padding(bottom = 12.dp)) }
                    item { ZeroChillPanel { ZeroChillActionRow("Favorite creators", "Browse creators you saved") {
                        activity.startActivity(Intent(activity, CreatorsActivity::class.java))
                    } } }
                    item { ZeroChillPanel { ZeroChillActionRow("Continue Watching", "Resume where you left off") {
                        activity.openSavedVideos(FavoritesActivity.START_CONTINUE)
                    } } }
                    item { ZeroChillPanel { ZeroChillActionRow("History", "See videos you watched") {
                        activity.openSavedVideos(FavoritesActivity.START_HISTORY)
                    } } }
                    item { ZeroChillPanel { ZeroChillActionRow("Watch Later", "Open your saved queue") {
                        activity.openSavedVideos(FavoritesActivity.START_WATCH_LATER)
                    } } }
                    item { ZeroChillPanel { ZeroChillActionRow("Downloads", "Watch videos available offline") {
                        activity.startActivity(Intent(activity, DownloadedActivity::class.java))
                    } } }
                }
            }
        }
    }
}
