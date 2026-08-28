package com.packatrack.core.json

import org.json.JSONObject

/** Thin conveniences so carrier parsers stay readable and null-safe. */
object JsonUtil {
    fun objOrNull(json: String?): JSONObject? =
        if (json.isNullOrBlank()) null else runCatching { JSONObject(json) }.getOrNull()

    fun stringOr(receiver: JSONObject?, key: String): String? =
        receiver?.optString(key)?.takeIf { it.isNotBlank() }
}
