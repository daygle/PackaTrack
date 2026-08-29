package com.packatrack.core.model

/**
 * A shipment as tracked by PackaTrack - a parcel that may pass through several
 * carriers and tracking numbers over its lifetime.
 */
data class Shipment(
    val trackingNumber: String,
    /** Most recent raw status code reported for the current leg. */
    val latestStatusCode: String? = null,
    /** Normalized status of the current leg. */
    val currentStatus: ShipmentStatus = ShipmentStatus.UNKNOWN,
    /** Declared weight in grams (from AliExpress order or carrier scans). */
    val declaredWeightGrams: Double? = null,
    /** Latest observed physical length/width/height in cm, if known. */
    val dimensionsCm: Dimensions? = null,
    val events: List<TrackingEvent> = emptyList(),
) {
    companion object
}

/** Parcel dimensions in centimetres. */
data class Dimensions(
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
)
