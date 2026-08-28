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

/**
 * Display name for a parcel: an explicit custom name if set, otherwise the orders it
 * carries (e.g. "Blue widget +1 more"), falling back to the first tracking number.
 */
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

fun statusLabel(code: String?): String =
    code?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Waiting for scans"

/**
 * The status shown for a whole parcel — the furthest-along of its couriers, but an
 * exception on any leg always wins so problems surface.
 */
private val STATUS_ORDER = listOf(
    "DELIVERED",
    "OUT_FOR_DELIVERY",
    "PICKUP_AVAILABLE",
    "IN_TRANSIT",
    "LABEL_CREATED",
)

fun overallStatusCode(legs: List<TrackingLegEntity>): String? {
    val codes = legs.asSequence().mapNotNull { it.lastStatusCode?.uppercase() }.toList()
    if (codes.isEmpty()) return null
    if (codes.any { it == "EXCEPTION" }) return "EXCEPTION"
    return STATUS_ORDER.firstOrNull { it in codes } ?: codes.first()
}

/** Calculates how many days a parcel has been active. */
fun daysInTransit(createdAt: Long, lastStatusCode: String?): Int {
    val now = System.currentTimeMillis()
    val diff = now - createdAt
    val days = (diff / (1000 * 60 * 60 * 24)).toInt()
    return days.coerceAtLeast(0)
}
