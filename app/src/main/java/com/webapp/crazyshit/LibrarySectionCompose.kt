package com.webapp.crazyshit

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.webapp.crazyshit.ui.ZeroChillTheme

/** Presentation-only bridge; existing rails keep their image targets and horizontal state. */
internal object LibrarySectionCompose {
    @JvmStatic fun create(context: Context, title: String, actionLabel: String, action: Runnable): ComposeView =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                ZeroChillTheme {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).semantics { heading(); contentDescription = title })
                        TextButton(onClick = action::run, modifier = Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = "$actionLabel $title" }) { Text(actionLabel) }
                    }
                }
            }
        }
}
