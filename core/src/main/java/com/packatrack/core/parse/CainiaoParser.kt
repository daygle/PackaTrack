package com.packatrack.core.parse

import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.TrackingEvent
import com.packatrack.core.util.TimeUtil
import org.json.JSONObject

/**
 * Parser for Cainiao's public global detail JSON:
 *   https://global.cainiao.com/global/detail.json?mailNos=...&lang=en
 *
 * Known shapes:
 *  A) {"data":{"<number>":{"status":"DELIVERED","weight":0.352,"sections":[
 *        {"sectionType":"ORIGIN_COUNTRY",...,"detailList":[{"time":"2024-01-02 10:11",
 *          "desc":"Parcel data processed","city":"Hangzhou","actionCode":"CW_FUNCTION_GOT"}]}]}}}
 *  B) {"data":{"<number>":{ "logisticsTrace":{"traceNodeList":[
 *        {"time":"2024-01-03 08:00","description":"Arrived at sorting center","location":"Sydney"}]}}}}
 *
 * Weight is reported in kilograms; PackaTrack stores grams.
 */
object CainiaoParser {

    fun parse(json: String): Snapshot? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val data = root.optJSONObject("data") ?: return null
        if (data.length() == 0) return null

        val names = data.names() ?: return null
        val number = names.getString(0)
        val pkg = data.optJSONObject(number) ?: return null

        val events = mutableListOf<TrackingEvent>()

        pkg.optJSONArray("sections")?.let { sections ->
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                val details = section.optJSONArray("detailList") ?: continue
                for (j in 0 until details.length()) {
                    val d = details.optJSONObject(j) ?: continue
                    events += TrackingEvent(
                        trackingNumber = number,
                        timeMs = TimeUtil.parse(d.optString("time")),
                        description = d.optString("desc").ifBlank { d.optString("status") },
                        location = d.optString("city").takeIf { it.isNotBlank() }
                            ?: d.optString("country").takeIf { it.isNotBlank() },
                        statusCode = mapCode(firstNonBlank(d, "actionCode", "standStillCode", "status")),
                    )
                }
            }
        }

        if (events.isEmpty()) {
            val nodes = pkg.optJSONObject("logisticsTrace")
                ?.optJSONArray("traceNodeList")
            if (nodes != null) {
                for (i in 0 until nodes.length()) {
                    val n = nodes.optJSONObject(i) ?: continue
                    events += TrackingEvent(
                        trackingNumber = number,
                        timeMs = TimeUtil.parse(n.optString("time")),
                        description = n.optString("description").ifBlank { "Update" },
                        location = n.optString("location").takeIf { it.isNotBlank() },
                        statusCode = n.optString("status").takeIf { it.isNotBlank() },
                    )
                }
            }
        }

        if (events.isEmpty() && firstNonBlank(pkg, "status") == null) return null

        events.sortByDescending { it.timeMs ?: Long.MAX_VALUE }

        return Snapshot(
            trackingNumber = number,
            weightGrams = pkg.optDoubleOrNull("weight")?.times(1000.0),
            dimensionsCm = null,
            events = events,
        )
    }

    /** Maps Cainiao's scanType/actionCodes onto PackaTrack status codes. */
    fun mapCode(raw: String?): String? {
        val c = raw?.uppercase() ?: return null
        return when {
            c.contains("SIGNED") || c.contains("DELIVER") -> "DELIVERED"
            c.contains("OUT_FOR_DELIVERY") || c == "DISPATCH" -> "OUT_FOR_DELIVERY"
            c.contains("FAIL") || c.contains("ABNORMAL") || c.contains("RETURN") -> "EXCEPTION"
            c.contains("GOT") || c.contains("ACCEPT") -> "IN_TRANSIT"
            else -> raw
        }
    }

    private fun firstNonBlank(obj: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { obj.optString(it).takeIf { s -> s.isNotBlank() } }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key)) return null
        return when (val v = opt(key)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }
}
