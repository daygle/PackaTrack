package com.packatrack.core.parse

import com.packatrack.core.json.JsonUtil
import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.TrackingEvent
import com.packatrack.core.util.TimeUtil
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for iMile's customer-facing track API used by their web tracker:
 *   https://customer.track.imile.com/...  (JSON with {"code":200,"data":{"records":[...]}})
 *
 * Known shapes:
 *  A) {"code":"200","data":{"waybillNo":"..","weight":"12.5","status":"...", "records":[
 *        {"time":"2026-07-01 10:22","status":"In transit","content":"Arrived at Sydney hub",
 *          "location":"Sydney AU"}]}}
 *  B) code != "200" / {"success":false,"message":"waybill not found"} → null (caller treats as not-found)
 */
object ImileParser {

    fun parse(json: String, requestedNumber: String): Snapshot? {
        val root = JsonUtil.objOrNull(json) ?: return null

        val code = JsonUtil.stringOr(root, "code") ?: root.optString("code")
        if (code.isNotBlank() && code != "200" && !root.optBoolean("success", true)) return null

        val data = root.optJSONObject("data") ?: return null

        val records = firstArray(data, "records", "list", "traceEvents", "events") ?: JSONArray()
        val number = firstNonBlank(data, "waybillNo", "trackingNumber").takeIf { it != null }
            ?.ifBlank { requestedNumber } ?: requestedNumber

        val events = mutableListOf<TrackingEvent>()
        for (i in 0 until records.length()) {
            val r = records.optJSONObject(i) ?: continue
            val desc = firstNonBlank(r, "content", "description", "activity", "statusDetail").orEmpty()
            if (desc.isBlank()) continue
            events += TrackingEvent(
                trackingNumber = number,
                timeMs = TimeUtil.parse(
                    firstNonBlank(r, "time", "occurTime", "createTime", "scanTime"),
                ),
                description = desc,
                location = firstNonBlank(r, "location", "city", "siteName"),
                statusCode = mapToStatus(firstNonBlank(r, "status", "activity").orEmpty()),
            )
        }

        if (events.isEmpty() && firstNonBlank(data, "status") == null) return null
        events.sortByDescending { it.timeMs ?: Long.MAX_VALUE }

        // weight may be kg ("12.5") or grams; iMile uses kg strings.
        val weightGrams = when {
            data.has("weight") -> data.optString("weight").toDoubleOrNull()?.times(1000.0)
            else -> null
        }

        return Snapshot(
            trackingNumber = number,
            weightGrams = weightGrams,
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
}
