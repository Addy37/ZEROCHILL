package com.addy37.crazyshitadmin

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val statuses = linkedMapOf("submitted" to "New", "reviewing" to "Reviewing", "planned" to "Planned",
    "in_progress" to "In Progress", "completed" to "Completed", "declined" to "Declined")
internal fun typeLabel(type: String) = when (type) {
    "bug_report" -> "Bug report"
    "feature_request" -> "Feature request"
    else -> "General feedback"
}
internal fun statusLabel(status: String) = statuses[status] ?: friendly(status)
internal fun friendly(value: String) = value.replace('_', ' ').replaceFirstChar { it.titlecase() }
internal fun dateLabel(raw: String): String = try {
    DateTimeFormatter.ofPattern("MMM d · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.parse(raw))
} catch (_: Exception) { raw.takeUnless { it == "null" }.orEmpty() }
internal fun number(value: Long) = String.format(Locale.getDefault(), "%,d", value)
internal fun JSONObject.keysList() = keys().asSequence().toList().sorted()
internal fun canonical(value: Any?): String = when(value) {
    is JSONObject -> value.keysList().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
    else -> JSONObject.valueToString(value)
}

/** Validation belongs to an exact immutable candidate. Any edit revokes publication permission. */
internal data class ConfigDraft(val baseline: String, val text: String, val published: Long,
    val validated: String = "") {
    val dirty: Boolean get() = canonical(JSONObject(baseline)) != canonical(JSONObject(text))
    val canPublish: Boolean get() = validated.isNotEmpty() && validated == text
    val candidateVersion: Long get() = maxOf(published, JSONObject(text).optLong("configVersion")) +
        if (canPublish) 0 else 1
    fun edit(next: JSONObject) = copy(text = next.toString(), validated = "")
    fun discard() = copy(text = baseline, validated = "")
    fun candidate(): String = JSONObject(text).put("configVersion", candidateVersion)
        .put("updatedAt", Instant.now().toString()).toString()
    fun acceptValidation(candidate: String) = copy(text = candidate, validated = candidate)
    fun changes(): List<String> {
        val before = JSONObject(baseline)
        val after = JSONObject(text)
        val result = mutableListOf<String>()
        fun compare(a: JSONObject, b: JSONObject, path: String) {
            (a.keysList() + b.keysList()).distinct().sorted().forEach { key ->
                if (path.isEmpty() && key in listOf("configVersion", "updatedAt")) return@forEach
                val name = if (path.isEmpty()) key else "$path.$key"
                val left = a.opt(key); val right = b.opt(key)
                if (left is JSONObject && right is JSONObject) compare(left, right, name)
                else if (canonical(left) != canonical(right)) result.add(name)
            }
        }
        compare(before, after, "")
        return result
    }
}
internal fun filterFeedback(items: List<AdminRepository.Item>, query: String, type: String,
    status: String, unread: Boolean, oldest: Boolean): List<AdminRepository.Item> = items.filter {
    (type.isEmpty() || it.type == type) && (status.isEmpty() || it.status == status) &&
        (!unread || it.unreadCount > 0) && (query.isBlank() ||
        listOf(it.message, it.lastMessage, it.device, it.section, it.appVersion).any { text -> text.contains(query.trim(), true) })
}.sortedWith(compareBy<AdminRepository.Item> { runCatching { Instant.parse(it.lastMessageAt) }.getOrDefault(Instant.EPOCH) }
    .let { if (oldest) it else it.reversed() })

internal fun realDevice(value: String): Boolean {
    val v = value.trim().lowercase(Locale.US)
    return v.isNotEmpty() && v != "unknown" && listOf("android sdk built for x86", "sdk_gphone",
        "generic_x86", "aosp_x86", "emulator", "goldfish", "ranchu").none { v.contains(it) }
}
internal fun deviceName(raw: String): String {
    val names = linkedMapOf("cph2655" to "OnePlus 13", "sm-s948" to "Samsung Galaxy S26 Ultra",
        "sm-s947" to "Samsung Galaxy S26+", "sm-s942" to "Samsung Galaxy S26", "sm-s938" to "Samsung Galaxy S25 Ultra",
        "sm-s937" to "Samsung Galaxy S25 Edge", "sm-s936" to "Samsung Galaxy S25+", "sm-s931" to "Samsung Galaxy S25",
        "sm-s928" to "Samsung Galaxy S24 Ultra", "sm-s926" to "Samsung Galaxy S24+", "sm-s921" to "Samsung Galaxy S24",
        "sm-s918" to "Samsung Galaxy S23 Ultra", "sm-s916" to "Samsung Galaxy S23+", "sm-s911" to "Samsung Galaxy S23",
        "sm-f966" to "Samsung Galaxy Z Fold7", "sm-f766" to "Samsung Galaxy Z Flip7", "sm-f956" to "Samsung Galaxy Z Fold6",
        "sm-f741" to "Samsung Galaxy Z Flip6")
    return names.entries.firstOrNull { raw.contains(it.key, true) }?.value ?: raw.trim().ifEmpty { "Unknown" }
}
