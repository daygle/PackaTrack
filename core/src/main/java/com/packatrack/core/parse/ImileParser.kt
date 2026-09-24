package com.packatrack.core.parse

import com.packatrack.core.json.JsonUtil
import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.TrackingEvent
import com.packatrack.core.util.TimeUtil
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for iMile's customer-facing track API used by their web tracker:
 *   https://www.imile.com/track?trackingNumbers=..
 *   GET /saastms/mobileWeb/track/query?waybillNo=..&code=MD5(waybillNo + salt)
 *
 * Known shapes:
 *  A) live envelope: {"status":"success","resultObject":{"waybillNo":"..","country":"..",
 *       "trackInfos":[{"time":"2026-07-01 10:22:00","content":"Arrived at Sydney hub",
 *         "trackStageTx":"In Transit","trackStage":2030,"operateStationName":"Sydney"}]}}
 *  B) legacy envelope: {"code":"200","data":{"waybillNo":"..","weight":"12.5","status":"...",
 *       "records":[{"time":"2026-07-01 10:22","status":"In transit",
 *         "content":"Arrived at Sydney hub","location":"Sydney AU"}]}}
 *  C) {"status":"error",..} / code != "200" / {"success":false} / no payload -> null
 *     (caller treats as not-found)
 */
object ImileParser {

    fun parse(json: String, requestedNumber: String): Snapshot? {
        val root = JsonUtil.objOrNull(json) ?: return null

        val code = JsonUtil.stringOr(root, "code") ?: root.optString("code")
        if (code.isNotBlank() && (code != "200") && (!root.optBoolean("success", true))) return null

        // The live envelope reports failures through root "status"; anything but
        // "success" means iMile has nothing to show for this number.
        val status = JsonUtil.stringOr(root, "status")
        if (status != null && !status.equals("success", ignoreCase = true)) return null

        // Live envelope keeps the payload under "resultObject", the legacy one under "data".
        val result = root.optJSONObject("resultObject")
        val data = result ?: root.optJSONObject("data") ?: return null

        val records = firstArray(data, "trackInfos", "records", "list", "traceEvents", "events")
            ?: JSONArray()
        val number = firstNonBlank(data, "waybillNo", "trackingNumber") ?: requestedNumber

        val events = mutableListOf<TrackingEvent>()
        for (i in 0 until records.length()) {
            val r = records.optJSONObject(i) ?: continue
            val desc = firstNonBlank(
                r, "content", "description", "activity", "statusDetail", "trackStageTx",
            ).orEmpty()
            if (desc.isBlank()) continue
            events += TrackingEvent(
                trackingNumber = number,
                timeMs = TimeUtil.parse(
                    firstNonBlank(r, "time", "occurTime", "createTime", "scanTime"),
                ),
                description = desc,
                location = firstNonBlank(r, "location", "city", "siteName", "operateStationName"),
                statusCode = mapToStatus(firstNonBlank(r, "trackStageTx", "status", "activity").orEmpty())
                    ?: stageToStatus(firstNonBlank(r, "trackStage")),
            )
        }

        // No scans and no shipment status: the carrier has no history for this number.
        // A waybill iMile did resolve (live envelope) still returns an empty timeline so
        // the UI can tell "no scans yet" apart from "fetch failed".
        if (events.isEmpty() && firstNonBlank(data, "status") == null && result == null) return null
        events.sortByDescending { it.timeMs ?: Long.MAX_VALUE }

        return Snapshot(
            trackingNumber = number,
            dimensionsCm = null,
            events = events,
        )
    }

    private fun firstArray(data: JSONObject, vararg keys: String): JSONArray? =
        keys.firstNotNullOfOrNull { data.optJSONArray(it) }

    private fun firstNonBlank(obj: JSONObject?, vararg keys: String): String? =
        obj?.let { o -> keys.firstNotNullOfOrNull { k -> o.optString(k).takeIf { s -> s.isNotBlank() } } }

    fun mapToStatus(text: String): String? {
        val t = text.lowercase()
        return when {
            t.contains("delivered") || t.contains("signed") -> "DELIVERED"
            t.contains("out for delivery") || t.contains("on vehicle") -> "OUT_FOR_DELIVERY"
            t.contains("held") && t.contains("collection") -> "PICKUP_AVAILABLE"
            t.contains("fail") || t.contains("refus") || t.contains("return") ||
                t.contains("damage") || t.contains("lost") -> "EXCEPTION"
            t.contains("received") || t.contains("picked up") || t.contains("collected from shipper")
                -> "IN_TRANSIT"
            t.contains("transit") || t.contains("arrived") || t.contains("departed") ||
                t.contains("sorting") || t.contains("flight") -> "IN_TRANSIT"
            t.isBlank().not() && (t.contains("created") || t.contains("booked")) -> "LABEL_CREATED"
            else -> null
        }
    }

    /**
     * iMile's numeric trackStage codes, used when the human-readable trackStageTx (or
     * status) text does not map to a known status. Codes as served by the live endpoint:
     * 1001 = order created, 2030 = in transit, 2050 = out for delivery, 2060 = delivered.
     */
    private fun stageToStatus(stage: String?): String? = when (stage) {
        "1001" -> "LABEL_CREATED"
        "2030" -> "IN_TRANSIT"
        "2050" -> "OUT_FOR_DELIVERY"
        "2060" -> "DELIVERED"
        else -> null
    }
}
