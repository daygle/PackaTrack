package com.packatrack.core.parse

import com.packatrack.core.json.JsonUtil
import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.TrackingEvent
import com.packatrack.core.util.TimeUtil
import org.json.JSONArray
import org.json.JSONObject

/**
 * Best-effort parser for Aramex tracking JSON. Aramex's shipment-tracking response nests scan
 * rows under a results array, each row carrying an `UpdateDescription` / `UpdateDateTime` /
 * `UpdateLocation`. Public shapes drift, so this is tolerant and returns null when it can't find
 * any recognisable events (the caller treats null as "carrier unreachable / not found").
 *
 * Tolerated shapes (any of):
 *  - `{"TrackingResults":[{"WaybillNumber":"..","Value":[{"UpdateDescription":"..", ...}]}]}`
 *  - `{"result":{"waybill":"..","events":[{"description":"..","date":"..","location":".."}]}}`
 *  - `{"data":{"records":[{"description":"..","time":"..","location":".."}]}}`
 */
object AramexParser {

    fun parse(json: String, requestedNumber: String): Snapshot? {
        val root = JsonUtil.objOrNull(json) ?: return null
        if (root.has("error") || root.optBoolean("HasErrors", false)) return null

        val rows = collectRows(root)
        if (rows.length() == 0) return null

        val events = mutableListOf<TrackingEvent>()
        for (i in 0 until rows.length()) {
            val r = rows.optJSONObject(i) ?: continue
            val desc = firstNonBlank(
                r, "UpdateDescription", "description", "content", "activity", "ProblemCode",
            ).orEmpty()
            if (desc.isBlank()) continue
            val date = firstNonBlank(r, "UpdateDateTime", "date", "time", "occurTime", "scanTime")
            events += TrackingEvent(
                trackingNumber = requestedNumber,
                timeMs = TimeUtil.parse(date),
                description = desc,
                location = firstNonBlank(r, "UpdateLocation", "location", "city"),
                statusCode = ImileParser.mapToStatus(desc),
            )
        }

        if (events.isEmpty()) return null
        events.sortByDescending { it.timeMs ?: Long.MAX_VALUE }
        return Snapshot(requestedNumber, null, events)
    }

    /** Finds the array of scan rows regardless of which envelope Aramex used. */
    private fun collectRows(root: JSONObject): JSONArray {
        firstArray(root, "TrackingResults")?.let { results ->
            // TrackingResults may itself hold rows, or wrap them under a Value/Value array.
            val nested = JSONArray()
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val inner = firstArray(obj, "Value", "value", "events", "records")
                if (inner != null) {
                    for (j in 0 until inner.length()) inner.optJSONObject(j)?.let { nested.put(it) }
                } else {
                    nested.put(obj)
                }
            }
            if (nested.length() > 0) return nested
        }
        val data = root.optJSONObject("result") ?: root.optJSONObject("data")
        firstArray(data ?: root, "events", "records", "list", "trackingInfo")?.let { return it }
        return JSONArray()
    }

    private fun firstArray(obj: JSONObject?, vararg keys: String): JSONArray? =
        obj?.let { o -> keys.firstNotNullOfOrNull { o.optJSONArray(it) } }

    private fun firstNonBlank(obj: JSONObject?, vararg keys: String): String? =
        obj?.let { o -> keys.firstNotNullOfOrNull { k -> o.optString(k).takeIf { s -> s.isNotBlank() } } }
}
