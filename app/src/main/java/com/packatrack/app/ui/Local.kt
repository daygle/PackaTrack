package com.packatrack.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.packatrack.app.AppContainer
import com.packatrack.app.PackaTrackApp
import com.packatrack.app.data.db.OrderItemEntity
import com.packatrack.app.data.db.ShipmentEntity
import com.packatrack.app.data.db.TrackingLegEntity

@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as PackaTrackApp).container

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

private val STATUS_ORDER = listOf(
    "DELIVERED",
    "OUT_FOR_DELIVERY",
    "PICKUP_AVAILABLE",
    "IN_TRANSIT",
    "LABEL_CREATED",
)

fun overallStatusCode(legs: List<TrackingLegEntity>): String? {
    val codes = legs.asSequence()
        .mapNotNull { it.lastStatusCode?.trim()?.uppercase()?.takeIf(String::isNotEmpty) }
        .toList()
    if (codes.isEmpty()) return null
    if (codes.any { it == "EXCEPTION" || it.startsWith("EXCEPTION_") }) return "EXCEPTION"
    return STATUS_ORDER.firstOrNull { it in codes } ?: codes.first()
}

fun daysInTransit(startMs: Long?): Int {
    if (startMs == null || startMs <= 0L) return 0
    val day = 24L * 60 * 60 * 1000
    return ((System.currentTimeMillis() / day) - (startMs / day)).toInt().coerceAtLeast(0)
}
