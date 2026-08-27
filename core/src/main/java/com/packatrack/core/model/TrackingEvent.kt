package com.packatrack.core.model

/** A single scan / checkpoint on a parcel's journey. */
data class TrackingEvent(
    val trackingNumber: String,
    /** Epoch milliseconds, or null when the carrier reports no precise time. */
    val timeMs: Long?,
    val description: String,
    val location: String? = null,
    val statusCode: String? = null,
)
