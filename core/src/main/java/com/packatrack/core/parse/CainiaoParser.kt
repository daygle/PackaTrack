package com.packatrack.core.parse

import com.packatrack.core.json.JsonUtil
import com.packatrack.core.model.CarrierProgress
import com.packatrack.core.model.JourneyStage
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
            ?: LATEST_NUMBER_LABEL.find(pkg.toString())?.groupValues?.getOrNull(1)
        latestTrackingNumber?.let(relatedNumbers::add)
        // The live global response carries the downstream/last-mile number in `copyRealMailNo`
        // (already clean) and `realMailNo` (labelled, e.g. "Latest Tracking Number:\t36YPH…").
        pkg.optString("copyRealMailNo").takeIf { it.isNotBlank() && it != number }?.let(relatedNumbers::add)
        pkg.optString("realMailNo").takeIf { it.isNotBlank() }?.let { labelled ->
            TRAILING_NUMBER.find(labelled.trim())?.groupValues?.getOrNull(1)
                ?.takeIf { it != number }?.let(relatedNumbers::add)
        }
        listOf("trackingNumber", "trackingNo", "mailNo", "waybillNo", "waybillNumber", "lastMileTrackingNo", "lastMileTrackingNumber")
            .forEach { key ->
                pkg.optString(key).takeIf { it.isNotBlank() && it != number }?.let(relatedNumbers::add)
            }
        pkg.optJSONArray("alternateArticles")?.let { articles ->
            for (i in 0 until articles.length()) {
                when (val value = articles.opt(i)) {
                    is JSONObject -> listOf("trackingNumber", "trackingNo", "mailNo", "waybillNo", "waybillNumber")
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

        // The carrier's own route, e.g. Mainland China -> Australia -> Delivered, with the
        // fraction it considers complete. Only shape C carries this; the others simply report
        // nothing and the detail screen falls back to its own steps.
        val progress = pkg.optJSONObject("processInfo")?.let { info ->
            val rate = info.optDouble("progressRate", Double.NaN)
                .takeIf { !it.isNaN() && it in 0.0..1.0 }
            val stages = info.optJSONArray("progressPointList")?.let { points ->
                (0 until points.length()).mapNotNull { i ->
                    val point = points.optJSONObject(i) ?: return@mapNotNull null
                    point.optString("pointName").trim().takeIf { it.isNotEmpty() }
                        ?.let { name -> JourneyStage(name, reached = point.optBoolean("light")) }
                }
            }.orEmpty()
            CarrierProgress(rate = rate, stages = stages).takeIf { rate != null || stages.isNotEmpty() }
        }

        return Snapshot(
            trackingNumber = number,
            events = events,
            relatedTrackingNumbers = relatedNumbers.toList(),
            progress = progress,
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
            // Live scans carry no city/country key at all: the facility is only in the
            // square-bracketed prefix of `standerdDesc` ("[Shatian Town] Departed from
            // sorting center"), so without this every scan loses its location.
            location = firstNonBlank(d, "city", "country", "location", "areaName")
                ?: BRACKET_CITY.find(d.optString("standerdDesc"))?.groupValues?.getOrNull(1)
                    ?.trim()?.takeIf { it.isNotEmpty() },
            statusCode = mapCode(firstNonBlank(d, "actionCode", "standStillCode", "status", "standerdDesc")),
        )
    }

    /**
     * Maps Cainiao's scanType/actionCodes (or, as a fallback, their English description) onto
     * PackaTrack status codes.
     *
     * Order matters: "DELIVER" is a substring of out-for-delivery and failed-delivery codes
     * ("OUT_FOR_DELIVERY", "GTMS_DELIVERING", "DELIVERY_FAILED"), so failures and
     * out-for-delivery are recognised before the delivered bucket.
     */
    fun mapCode(raw: String?): String? {
        raw ?: return null
        // A plain-text description rather than a code ("Delivery attempted - customer not
        // home"): the wording rules every carrier shares decide first, since the code rules
        // below would read any "DELIVER" in it as delivered.
        if (raw.any { it.isWhitespace() }) StatusText.map(raw)?.let { return it }
        val c = raw.uppercase().replace(' ', '_')
        return when {
            // Exception / failure
            c.contains("FAIL") || c.contains("ABNORMAL") || c.contains("RETURN") ||
                c.contains("LOST") || c.contains("CANCEL") -> "EXCEPTION"
            // Out for delivery
            c.contains("OUT_FOR_DELIVERY") || c == "DISPATCH" ||
                c.contains("OUT_DELIVERY") || c.contains("DELIVERING") -> "OUT_FOR_DELIVERY"
            // "UNDELIVERABLE" and "DELIVERY_UNSUCCESSFUL" contain DELIVER but mean the opposite,
            // so they must be claimed before the delivered bucket below.
            c.contains("UNDELIVER") || c.contains("NOT_DELIVERED") ||
                c.contains("UNSUCCESSFUL_DELIVER") || c.contains("DELIVERY_FAIL") -> "EXCEPTION"
            // Delivered ("SIGNED" as its own word, so e.g. "ASSIGNED" does not match)
            SIGNED.containsMatchIn(c) || c.contains("DELIVER") -> "DELIVERED"
            c.contains("LAST_MILE") -> "OUT_FOR_DELIVERY"
            // In transit - prefix-based patterns for Cainiao/UBI action codes.
            // CW_ is warehouse handling (inbound, sign-in) and PU_ is the carrier pickup; both
            // are movement, not arrival. This includes CW_SIGN_IN_SUCCESS, which is a stock-in
            // scan and is one letter away from the SIGNED signature check above.
            c.startsWith("LH_") || c.startsWith("CC_") || c.startsWith("SC_") ||
                c.startsWith("GWMS_") || c.startsWith("WM_") ||
                c.startsWith("CW_") || c.startsWith("PU_") ||
                c.contains("GOT") || c.contains("ACCEPT") -> "IN_TRANSIT"
            else -> raw
        }
    }

    /**
     * True when [body] is Alibaba's anti-bot interstitial (the "punish" page with a slider
     * captcha) rather than tracking data. Only meaningful for a body [parse] could not read.
     */
    fun isBotCheck(body: String): Boolean =
        BOT_CHECK_MARKERS.any { body.contains(it, ignoreCase = true) }

    private val BOT_CHECK_MARKERS = listOf("captcha", "punish", "x5sec", "verify")

    private fun firstNonBlank(obj: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { JsonUtil.stringOr(obj, it) }

    private val SIGNED = Regex("(^|[^A-Z])SIGNED")
    /** Facility name in the leading brackets of `standerdDesc`, e.g. "[Shatian Town] Departed". */
    private val BRACKET_CITY = Regex("^\\[([^\\]]{1,80})\\]")
    private val LATEST_NUMBER_LABEL =
        Regex("latest\\s+tracking\\s+number\\s*[:：]?\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
    private val TRAILING_NUMBER = Regex("([A-Za-z0-9]{6,})\\s*$")
}
