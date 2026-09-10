package com.packatrack.feature.common

import com.packatrack.core.db.OrderItemEntity
import com.packatrack.core.db.ShipmentEntity
import com.packatrack.core.db.TrackingLegEntity

/** Display name for a parcel, falling back from custom title to order name to tracking number. */
fun parcelName(
    shipment: ShipmentEntity,
    orders: List<OrderItemEntity>,
    legs: List<TrackingLegEntity>,
): String {
    shipment.title?.takeIf { it.isNotBlank() }?.let { return it }
    orders.firstOrNull()?.let { first ->
        return if (orders.size == 1) first.name else "${first.name}  +${orders.size - 1} more"
    }
    return legs.firstOrNull()?.trackingNumber ?: "Parcel"
}

fun statusLabel(code: String?): String = when (code?.trim()?.uppercase()) {
    "DELIVERED" -> "Delivered"
    "OUT_FOR_DELIVERY" -> "Out for delivery"
    "PICKUP_AVAILABLE" -> "Pickup available"
    "IN_TRANSIT" -> "In transit"
    "LABEL_CREATED" -> "Label created"
    "EXCEPTION" -> "Exception"
    null, "" -> "Waiting for scans"
    else -> code.trim().lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun daysInTransit(startMs: Long?): Int {
    if (startMs == null || startMs <= 0L) return 0
    val day = 24L * 60 * 60 * 1000
    return ((System.currentTimeMillis() / day) - (startMs / day)).toInt().coerceAtLeast(0)
}

/** Re-export of the shared heuristic in core, kept for existing feature-module callers. */
fun overallStatusCode(
    legs: List<TrackingLegEntity>,
    newestEventMsByLeg: Map<Long, Long?>? = null,
): String? = com.packatrack.core.model.overallStatusCode(legs, newestEventMsByLeg)
