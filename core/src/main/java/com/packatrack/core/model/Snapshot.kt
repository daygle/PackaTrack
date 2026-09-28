package com.packatrack.core.model

/** Everything we know about a parcel after one fetch from its current carrier. */
data class Snapshot(
    val trackingNumber: String,
    val dimensionsCm: Dimensions?,
    val events: List<TrackingEvent>,
    /** Alternate/current numbers reported by the carrier for the same parcel. */
    val relatedTrackingNumbers: List<String> = emptyList(),
)

/** Parcel dimensions in centimetres. */
data class Dimensions(
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
)
