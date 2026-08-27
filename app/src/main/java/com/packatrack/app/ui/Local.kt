package com.packatrack.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.packatrack.app.AppContainer
import com.packatrack.app.PackaTrackApp
import com.packatrack.app.data.db.TrackingLegEntity

@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as PackaTrackApp).container

fun statusLabel(code: String?): String =
    code?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Waiting for scans"

fun humanWeight(g: Double?): String = when {
    g == null -> "—"
    g >= 1000.0 -> String.format(java.util.Locale.US, "%.2f kg", g / 1000.0)
    else -> String.format(java.util.Locale.US, "%.0f g", g)
}

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
    val codes = legs.mapNotNull { it.lastStatusCode?.uppercase() }
    if (codes.isEmpty()) return null
    if (codes.any { it == "EXCEPTION" }) return "EXCEPTION"
    return STATUS_ORDER.firstOrNull { it in codes } ?: codes.first()
}

/** Best declared/observed weight to show for a parcel. */
fun parcelWeight(shipmentWeight: Double?, legs: List<TrackingLegEntity>): Double? =
    shipmentWeight ?: legs.mapNotNull { it.weightGrams }.maxOrNull()
