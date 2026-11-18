package com.packatrack.core.parse

import com.packatrack.core.json.JsonUtil
import com.packatrack.core.model.Snapshot
import com.packatrack.core.model.TrackingEvent
import com.packatrack.core.util.TimeUtil
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for Australia Post's digital API v2 track/events response:
 *   GET https://digitalapi.auspost.com.au/v2/postage/track/events?q=<NUMBER>
 *   header: AUTH-KEY <api key>            (free key from developers.auspost.com.au)
 *
 * Response shape:
 * {
 *   "queryTrackedResponseItems": [{
 *      "tracking_id": "...",
 *      "shipments": [{ "tracking_events": [
 *            {"event_date_time":"2026-06-12T09:04:00+10:00","event_type_description":
 *             "Delivered","description":"Delivered to letterbox","location":"SYDNEY NSW"},
 *            ...],
 *        "alternateArticles":[...], "statusSummary":"..." }]}]
 * }
 */
object AusPostParser {

    fun parse(json: String, requestedNumber: String): Snapshot? {
        val root = JsonUtil.objOrNull(json) ?: return null
        // A free-account response that isn't authorised looks like:
        // {"error":{"context":"/track/events?q=..","message":"AUTHENTICATION_FAILED"}}
        if (root.optJSONObject("error") != null || root.optString("errorCode").isNotBlank()) return null

        val items = root.optJSONArray("queryTrackedResponseItems")
            ?: root.optJSONArray("TrackedItems")
            ?: root.optJSONArray("trackingItems")
            ?: return null
        if (items.length() == 0) return null
        val item = items.optJSONObject(0) ?: return null

        val number = item.optString("tracking_id").ifBlank { requestedNumber }
        val events = mutableListOf<TrackingEvent>()
        val shipments = item.optJSONArray("shipments") ?: JSONArray()

        for (i in 0 until shipments.length()) {
            val shipment = shipments.optJSONObject(i) ?: continue
            collectEvents(shipment.optJSONArray("tracking_events"), number, events)
            collectEvents(shipment.optJSONArray("trackingEvents"), number, events)
            collectEvents(shipment.optJSONArray("events"), number, events)
            collectEvents(shipment.optJSONArray("articles"), number, events)
        }
        collectEvents(item.optJSONArray("events"), number, events)
        collectEvents(item.optJSONArray("tracking_events"), number, events)
        collectEvents(item.optJSONArray("trackingEvents"), number, events)

        if (events.isEmpty() &&
            JsonUtil.stringOr(item, "statusSummary") == null &&
            JsonUtil.stringOr(item, "status") == null
        ) return null
        events.sortByDescending { it.timeMs ?: Long.MAX_VALUE }

        return Snapshot(
            trackingNumber = number,
            events = events,
        )
    }

    private fun collectEvents(
        arr: JSONArray?,
        number: String,
        out: MutableList<TrackingEvent>,
    ) {
        arr ?: return
        for (i in 0 until arr.length()) {
            when (val node = arr.opt(i)) {
                is JSONObject -> {
                    // Read through JsonUtil so a JSON null (which Android's org.json renders as
                    // the literal text "null") falls through instead of becoming a scan.
                    val evType = JsonUtil.stringOr(node, "event_type_description")
                        ?: JsonUtil.stringOr(node, "eventDescription")
                        ?: JsonUtil.stringOr(node, "description").orEmpty()
                    val desc = JsonUtil.stringOr(node, "description") ?: evType
                    if (desc.isBlank() && evType.isBlank()) continue
                    out += TrackingEvent(
                        trackingNumber = number,
                        timeMs = TimeUtil.parse(node.optString("event_date_time")),
                        description = desc.ifBlank { evType },
                        location = JsonUtil.stringOr(node, "location"),
                        statusCode = StatusText.map(evType.ifBlank { desc }),
                    )
                }
                else -> Unit
            }
        }
    }
}
