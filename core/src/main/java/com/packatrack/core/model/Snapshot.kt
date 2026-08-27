package com.packatrack.core.model

/** Everything we know about a parcel after one fetch from its current carrier. */
data class Snapshot(
    val trackingNumber: String,
    val weightGrams: Double?,
    val dimensionsCm: Dimensions?,
    val events: List<TrackingEvent>,
) {
    companion object {
        fun empty(trackingNumber: String) =
            Snapshot(trackingNumber, null, null, emptyList())
    }
}
