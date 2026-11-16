package com.packatrack.core.model

/** Everything we know about a parcel after one fetch from its current carrier. */
data class Snapshot(
    val trackingNumber: String,
    val events: List<TrackingEvent>,
    /** Alternate/current numbers reported by the carrier for the same parcel. */
    val relatedTrackingNumbers: List<String> = emptyList(),
    /** Stages and percentage the carrier reports for the route, when it reports any. */
    val progress: CarrierProgress? = null,
)
