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
 *  C) {"success":true,"module":[{"mailNo":"<number>","status":"TRANSIT",
 *        "detailList":[{"time":1704189060000,"timeStr":"2024-01-02 10:11",
 *          "desc":"Parcel data processed","standerdDesc":"...","actionCode":"GWMS_ACCEPT"}]}]}
 *     This is what the live endpoint returns today - a top-level "module" array whose
 *     elements carry a flat "detailList". Shapes A/B are kept for older/alternate responses.
 *
 * Weight is reported in kilograms; PackaTrack stores grams.
 */
object CainiaoParser {

    fun parse(json: String): Snapshot? {
        val root = runCatching { JSONObject(json) }.getOrNull()
        if (root == null) {
            android.util.Log.w("CainiaoParser", "Failed to parse JSON: ${json.take(200)}")
            return null
        }

        // Shape C - live global endpoint: {"module":[{"mailNo":..,"detailList":[..]}]}
        root.optJSONArray("module")?.let { modules ->
            for (i in 0 until modules.length()) {
                val pkg = modules.optJSONObject(i) ?: continue
                val number = firstNonBlank(pkg, "mailNo", "mailNoList", "trackingNumber", "trackingNo", "waybillNo")
                    ?: continue
                extract(number, pkg)?.let { return it }
            }
        }

        // Shapes A/B - {"data":{"<number>":{..}}}
        val data = root.optJSONObject("data")
        if (data == null || data.length() == 0) {
            android.util.Log.d("CainiaoParser", "No data in response: ${json.take(300)}")
            return null
        }

        val names = data.names() ?: return null
        val number = names.getString(0)
        val pkg = data.optJSONObject(number) ?: return null
        return extract(number, pkg)
    }

    /** Builds a [Snapshot] from a single package object, whichever response shape it came from. */
    private fun extract(number: String, pkg: JSONObject): Snapshot? {
        val events = mutableListOf<TrackingEvent>()
        val relatedNumbers = linkedSetOf<String>()
        val latestTrackingNumber = listOf(
            "latestTrackingNumber", "latestTrackingNo", "lastMileTrackingNumber", "lastMileTrackingNo",
        ).firstNotNullOfOrNull { key -> pkg.optString(key).takeIf { it.isNotBlank() && it != number } }
            ?: Regex("latest\\s+tracking\\s+number\\s*[:：]?\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
                .find(pkg.toString())?.groupValues?.getOrNull(1)
        latestTrackingNumber?.let(relatedNumbers::add)
        // The live global response carries the downstream/last-mile number in `copyRealMailNo`
        // (already clean) and `realMailNo` (labelled, e.g. "Latest Tracking Number:\t36YPH…").
        pkg.optString("copyRealMailNo").takeIf { it.isNotBlank() && it != number }?.let(relatedNumbers::add)
        pkg.optString("realMailNo").takeIf { it.isNotBlank() }?.let { labelled ->
            Regex("([A-Za-z0-9]{6,})\\s*$").find(labelled.trim())?.groupValues?.getOrNull(1)
                ?.takeIf { it != number }?.let(relatedNumbers::add)
        }
        listOf("trackingNumber", "trackingNo", "mailNo", "waybillNo", "waybillNumber", "lastMileTrackingNo", "lastMileTrackingNumber")
            .forEach { key ->
                pkg.optString(key).takeIf { it.isNotBlank() && it != number }?.let(relatedNumbers::add)
            }
        pkg.optJSONArray("alternateArticles")?.let { articles ->
            for (i in 0 until articles.length()) {
                when (val value = articles.opt(i)) {
                    is org.json.JSONObject -> listOf("trackingNumber", "trackingNo", "mailNo", "waybillNo", "waybillNumber")
                        .firstNotNullOfOrNull { key -> value.optString(key).takeIf { it.isNotBlank() } }
                        ?.takeIf { it != number }?.let(relatedNumbers::add)
                    is String -> value.takeIf { it.isNotBlank() && it != number }?.let(relatedNumbers::add)
                }
            }
        }

        pkg.optJSONArray("sections")?.let { sections ->
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                val details = section.optJSONArray("detailList") ?: continue
                for (j in 0 until details.length()) {
                    val d = details.optJSONObject(j) ?: continue
                    events += detailEvent(number, d)
                }
            }
        }

        // Flat detailList directly on the package (shape C, the live global response).
        if (events.isEmpty()) {
            pkg.optJSONArray("detailList")?.let { details ->
                for (j in 0 until details.length()) {
                    val d = details.optJSONObject(j) ?: continue
                    events += detailEvent(number, d)
                }
            }
        }

        if (events.isEmpty()) {
            pkg.optJSONObject("logisticsTrace")?.optJSONArray("traceNodeList")?.let { nodes ->
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

        if (events.isEmpty() && (firstNonBlank(pkg, "status", "statusDesc") == null)) return null

        events.sortByDescending { it.timeMs ?: Long.MAX_VALUE }

        return Snapshot(
            trackingNumber = number,
            dimensionsCm = null,
            events = events,
            relatedTrackingNumbers = relatedNumbers.toList(),
        )
    }

    /** Maps one detail row (from `sections[].detailList` or a flat `detailList`) to an event. */
    private fun detailEvent(number: String, d: JSONObject): TrackingEvent {
        // The live endpoint reports `time` as epoch millis alongside a `timeStr`; older shapes
        // use a formatted `time` string. TimeUtil.parse handles both numbers and strings.
        val time = firstNonBlank(d, "time", "timeStr")
        return TrackingEvent(
            trackingNumber = number,
            timeMs = TimeUtil.parse(time),
            description = firstNonBlank(d, "desc", "standerdDesc", "statusDesc", "status") ?: "Update",
            location = firstNonBlank(d, "city", "country", "location", "areaName"),
            statusCode = mapCode(firstNonBlank(d, "actionCode", "standStillCode", "status", "standerdDesc")),
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
}
