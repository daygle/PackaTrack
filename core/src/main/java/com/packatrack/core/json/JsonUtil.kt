package com.packatrack.core.json

import org.json.JSONArray
import org.json.JSONObject

/** Thin conveniences so carrier parsers stay readable and null-safe. */
object JsonUtil {
    fun objOrNull(json: String?): JSONObject? =
        if (json.isNullOrBlank()) null else runCatching { JSONObject(json) }.getOrNull()

    fun arrOrNull(obj: JSONObject?, key: String): JSONArray? = obj?.optJSONArray(key)

    fun stringOr(JSONObject_Receiver: JSONObject?, key: String): String? =
        JSONObject_Receiver?.optString(key)?.takeIf { it.isNotBlank() }

    fun longOrNull(obj: JSONObject?, key: String): Long? {
        val v = obj?.optString(key)
        if (!v.isNullOrBlank()) {
            v.toLongOrNull()?.let { return it }
            // Many carriers return "yyyy-MM-dd HH:mm" or ISO strings; caller parses time.
            return null
        }
        val l = obj?.optLong(key, Long.MIN_VALUE) ?: Long.MIN_VALUE
        return if (l == Long.MIN_VALUE) null else l
    }

    fun doubleOrNull(obj: JSONObject?, key: String): Double? {
        if (obj == null || !obj.has(key)) return null
        return when (val v = obj.opt(key)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }
}
