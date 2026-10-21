package com.webapp.crazyshit

import android.content.DialogInterface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.webapp.crazyshit.ui.*

/** Presentation only. Every accepted action still runs in the existing Settings controller. */
internal class SettingsDialogs {
    private var current by mutableStateOf<Builder?>(null)
    fun builder() = Builder()
    fun title() = current?.heading.orEmpty()

    inner class Builder {
        internal var heading = ""
        internal var message = ""
        internal var choices = emptyList<String>()
        internal var selected = -1
        internal var selection: DialogInterface.OnClickListener? = null
        internal var positive: Pair<String, DialogInterface.OnClickListener?>? = null
        internal var negative: Pair<String, DialogInterface.OnClickListener?>? = null
        internal var neutral: Pair<String, DialogInterface.OnClickListener?>? = null
        internal var dangerous = false
        internal var selectable = false
        fun setTitle(value: String) = apply { heading = value }
        fun setMessage(value: String) = apply { message = value }
        fun selectable() = apply { selectable = true }
        fun destructive() = apply { dangerous = true }
        fun setItems(values: Array<String>, callback: DialogInterface.OnClickListener) = apply {
            choices = values.toList(); selection = callback
        }
        fun setSingleChoiceItems(values: Array<String>, checked: Int, callback: DialogInterface.OnClickListener) =
            setItems(values, callback).apply { selected = checked }
        fun setPositiveButton(label: String, callback: DialogInterface.OnClickListener?) = apply { positive = label to callback }
        fun setNegativeButton(label: String, callback: DialogInterface.OnClickListener?) = apply { negative = label to callback }
        fun setNeutralButton(label: String, callback: DialogInterface.OnClickListener?) = apply { neutral = label to callback }
        fun show() { current = this }
    }

    private val handle = object : DialogInterface {
        override fun cancel() { current = null }
        override fun dismiss() { current = null }
    }
    private fun accept(callback: DialogInterface.OnClickListener?, which: Int) {
        current = null // Clear first: callbacks can open the next confirmation.
        callback?.onClick(handle, which)
    }

    @Composable fun Render() {
        val model = current ?: return
        Dialog(onDismissRequest = { current = null }) {
            ZeroChillPanel {
                Text(model.heading, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp))
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                    if (model.message.isNotEmpty()) item {
                        if (model.selectable) SelectionContainer {
                            Text(model.message, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        } else Text(model.message, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    itemsIndexed(model.choices) { index, label ->
                        if (model.selected >= 0) {
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                RadioButton(index == model.selected, onClick = { accept(model.selection, index) })
                                Box(Modifier.weight(1f)) {
                                    ZeroChillActionRow(label) { accept(model.selection, index) }
                                }
                            }
                        } else ZeroChillActionRow(label) { accept(model.selection, index) }
                    }
                }
                // Vertical actions remain usable with large fonts and narrow windows.
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    model.positive?.let { (label, callback) ->
                        TextButton(onClick = { accept(callback, DialogInterface.BUTTON_POSITIVE) },
                            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(
                                contentColor = if (model.dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )) { Text(label) }
                    }
                    model.neutral?.let { (label, callback) ->
                        TextButton(onClick = { accept(callback, DialogInterface.BUTTON_NEUTRAL) },
                            modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                    model.negative?.let { (label, callback) ->
                        TextButton(onClick = { accept(callback, DialogInterface.BUTTON_NEGATIVE) },
                            modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                }
            }
        }
    }
}
