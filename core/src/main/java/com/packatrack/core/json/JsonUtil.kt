package com.packatrack.core.json

import org.json.JSONObject

/** Thin conveniences so carrier parsers stay readable and null-safe. */
object JsonUtil {
    fun objOrNull(json: String?): JSONObject? =
        if (json.isNullOrBlank()) null else runCatching { JSONObject(json) }.getOrNull()

    // Android's org.json renders a JSON null as the text "null" instead of an empty string,
    // so a missing value has to be checked explicitly before reading it as text.
    fun stringOr(receiver: JSONObject?, key: String): String? =
        receiver?.takeUnless { it.isNull(key) }?.optString(key)?.takeIf { it.isNotBlank() }
}
