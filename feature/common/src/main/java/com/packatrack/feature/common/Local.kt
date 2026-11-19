package com.packatrack.feature.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.packatrack.core.db.OrderItemEntity
import com.packatrack.core.db.ShipmentEntity
import com.packatrack.core.db.TrackingLegEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Display name for a parcel, falling back from custom title to order name to tracking number. */
fun parcelName(
    shipment: ShipmentEntity,
    orders: List<OrderItemEntity>,
    legs: List<TrackingLegEntity>,
): String {
    shipment.title?.takeIf { it.isNotBlank() }?.let { return it }
    orders.firstOrNull()?.let { first ->
        return if (orders.size == 1) first.name else "${first.name} +${orders.size - 1} more"
    }
    return newestTrackingNumber(legs) ?: "Parcel"
}

/**
 * The leg carrying the parcel's current number: the one added most recently.
 *
 * A parcel's number changes by gaining a leg - a hand-off to the local carrier, a second view of
 * the same parcel - so the newest leg is the number being tracked now, while the first leg keeps
 * the origin carrier's number the parcel was created with. Ties (rows without a createdAt) keep the
 * first leg, so the choice stays stable.
 */
fun newestLeg(legs: List<TrackingLegEntity>): TrackingLegEntity? = legs.maxByOrNull { it.createdAt }

/** The number shown for a parcel; see [newestLeg]. */
fun newestTrackingNumber(legs: List<TrackingLegEntity>): String? = newestLeg(legs)?.trackingNumber

/** String resource for a normalized status code, or null when the code is not one we know. */
@StringRes
fun statusLabelRes(code: String?): Int? = when (code?.trim()?.uppercase()) {
    "DELIVERED" -> R.string.status_delivered
    "OUT_FOR_DELIVERY" -> R.string.status_out_for_delivery
    "PICKUP_AVAILABLE" -> R.string.status_pickup_available
    "IN_TRANSIT" -> R.string.status_in_transit
    "LABEL_CREATED" -> R.string.status_label_created
    "EXCEPTION" -> R.string.status_exception
    null, "" -> R.string.status_waiting_for_scans
    else -> null
}

/** Localized label for a normalized status code. */
@Composable
fun statusLabel(code: String?): String {
    statusLabelRes(code)?.let { return stringResource(it) }
    // An unrecognised carrier code: show it tidied up rather than inventing a translation.
    return code.orEmpty().trim().lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun daysInTransit(startMs: Long?): Int {
    if (startMs == null || startMs <= 0L) return 0
    val day = 24L * 60 * 60 * 1000
    return ((System.currentTimeMillis() / day) - (startMs / day)).toInt().coerceAtLeast(0)
}

private const val DEFAULT_DATE_PATTERN = "dd MMM yyyy, HH:mm"

// Built once per pattern per thread: SimpleDateFormat is neither thread-safe nor cheap to
// construct, and these are called for every card and timeline row on every recomposition.
private val dateFormatters = object : ThreadLocal<HashMap<String, SimpleDateFormat>>() {
    override fun initialValue() = HashMap<String, SimpleDateFormat>()
}

/**
 * Formats a device-clock timestamp (e.g. a last sync, or a change the app recorded) with the
 * user's chosen pattern in the device's time zone, falling back to the default pattern if it is
 * invalid. Carrier scan times go through TimeUtil.format instead, which shows them as reported.
 */
fun formatLocalDateTime(timeMs: Long, pattern: String): String = dateFormatters.get()!!
    .getOrPut(pattern) {
        try {
            SimpleDateFormat(pattern, Locale.getDefault())
        } catch (_: IllegalArgumentException) {
            SimpleDateFormat(DEFAULT_DATE_PATTERN, Locale.getDefault())
        }
    }
    // A cached formatter keeps the zone it was built with; follow the device if it has changed
    // since (travel, or a manual change) instead of showing every time in the old zone.
    .apply { timeZone = TimeZone.getDefault() }
    .format(Date(timeMs))
